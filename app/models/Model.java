package models;

import play.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.sendgrid.SendGrid;
import com.sendgrid.SendGridException;

import org.apache.commons.codec.binary.Hex;
import org.bson.types.ObjectId;
import org.jongo.Jongo;
import org.jongo.marshall.jackson.oid.Id;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class Model {
    public static Configuration conf = Play.application().configuration();
    public static ObjectMapper mapper = new ObjectMapper();
    private static JsonStringEncoder encoder = JsonStringEncoder.getInstance();

    public static DB mongodb;
    public static Jongo jongo;

    private static JedisPool jedisPool;

    private static SendGrid sendgrid;

    public static void init() {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String mongodbHost = conf.getString("mongodb.host", "localhost");
        int mongodbPort = conf.getInt("mongodb.port", 27017);
        String mongodbDB = conf.getString("mongodb.db", "itooii");

        String redisHost = conf.getString("redis.host", "localhost");
        int redisPort = conf.getInt("redis.port", 6379);

        sendgrid = new SendGrid(conf.getString("sendgrid.api_key"));

        try {
            mongodb = new MongoClient(mongodbHost, mongodbPort).getDB(mongodbDB);
            jongo = new Jongo(mongodb);

            jedisPool = new JedisPool(redisHost, redisPort);
        } catch (UnknownHostException e) {
            //TODO
        } catch (JedisConnectionException e) {
            //TODO
        }
    }

    public static void errorlog(Object error) {
        Logger.error(error.toString());
    }

    public static void errorlog(Throwable cause) {
        StringWriter errors = new StringWriter();
        cause.printStackTrace(new PrintWriter(errors));
        Logger.error(errors.toString());
    }

    public static StringBuilder toJson(Model model) {
        StringBuilder result = new StringBuilder(512);

        objectToJson(model, result);

        return result;
    }

    public static StringBuilder toJson(List<? extends Model> models) {
        StringBuilder result = new StringBuilder(512);

        collectionToJson(models, result);

        return result;
    }

    private static void objectToJson(Object object, StringBuilder result) {
        try {
            result.append("{");

            List<Field> fields = new ArrayList<Field>();

            Class<?> clazz = object.getClass();
            while (clazz != Model.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    if (field.get(object) != null && !Modifier.isStatic(field.getModifiers()))
                        fields.add(field);
                }
                clazz = clazz.getSuperclass();
            }

            Iterator<Field> iterator = fields.iterator();
            while (iterator.hasNext()) {
                Field field = iterator.next();

                field.setAccessible(true);
                String name = field.getName();
                Object value = field.get(object);

                Id id = field.getAnnotation(Id.class);
                if (id != null) {
                    result.append("\"_id\":\"").append(value).append("\"") ;
                    if (iterator.hasNext())
                        result.append(",");

                    continue;
                }

                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                if (jsonProperty != null)
                    name = jsonProperty.value();

                if (value == null)
                    continue;

                clazz = value.getClass();
                if (clazz == Boolean.class) {
                    result.append("\"").append(name).append("\":").append((Boolean) value);
                } else if (clazz == Date.class) {
                    Date date = (Date) value;
                    result.append("\"").append(name).append("\":").append(date.getTime());
                } else if (clazz == Integer.class) {
                    result.append("\"").append(name).append("\":").append((Integer) value);
                } else if (clazz == String.class) {
                    result.append("\"").append(name).append("\":\"")
                        .append(encoder.quoteAsString((String) value)).append("\"");
                } else if (value instanceof Collection) {
                    result.append("\"").append(name).append("\":");
                    collectionToJson((Collection<?>) value, result);
                } else if (value instanceof Model) {
                    result.append("\"").append(name).append("\":");
                    objectToJson(value, result);
                } else {
                    result.append("\"").append(name).append("\":\"").append(value).append("\"");
                }

                if (iterator.hasNext())
                    result.append(",");
            }

            result.append("}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings(value = "unchecked")
    private static void collectionToJson(Collection<?> collection, StringBuilder result) {
        result.append("[");

        if (!collection.isEmpty()) {
            Object object = collection.iterator().next();
            Class<? extends Object> clazz = object.getClass();
            if (clazz == Boolean.class) {
                Iterator<Boolean> iterator = (Iterator<Boolean>) collection.iterator();
                while (iterator.hasNext()) {
                    result.append(iterator.next());
                    if (iterator.hasNext())
                        result.append(",");
                }
            } else if (clazz == Date.class) {
                Iterator<Date> iterator = (Iterator<Date>) collection.iterator();
                while (iterator.hasNext()) {
                    result.append(iterator.next().getTime());
                    if (iterator.hasNext())
                        result.append(",");
                }
            } else if (clazz == Integer.class) {
                Iterator<Integer> iterator = (Iterator<Integer>) collection.iterator();
                while (iterator.hasNext()) {
                    result.append(iterator.next());
                    if (iterator.hasNext())
                        result.append(",");
                }
            } else if (clazz == String.class) {
                Iterator<String> iterator = (Iterator<String>) collection.iterator();
                while (iterator.hasNext()) {
                    result.append("\"").append(encoder.quoteAsString(iterator.next())).append("\"");
                    if (iterator.hasNext())
                        result.append(",");
                }
            } else if (object instanceof Collection) {
                Iterator<Collection<?>> iterator = (Iterator<Collection<?>>) collection.iterator();
                while (iterator.hasNext()) {
                    collectionToJson(iterator.next(), result);
                    if (iterator.hasNext())
                        result.append(",");
                }
            } else if (object instanceof Model) {
                Iterator<Object> iterator = (Iterator<Object>) collection.iterator();
                while (iterator.hasNext()) {
                    objectToJson(iterator.next(), result);
                    if (iterator.hasNext())
                        result.append(",");
                }
            } else {
                Iterator<Object> iterator = (Iterator<Object>) collection.iterator();
                while (iterator.hasNext()) {
                    result.append("\"").append(iterator.next()).append("\"");
                    if (iterator.hasNext())
                        result.append(",");
                }
            }
        }

        result.append("]");
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] digest = md.digest();

            return new String(Hex.encodeHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String get(String key) {
        Jedis jedis = jedisPool.getResource();
        String result = null;

        try {
            result = jedis.get(key);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static void set(String key, String value) {
        set(key, 3600, value);
    }

    public static void set(String key, int seconds, String value) {
        Jedis jedis = jedisPool.getResource();

        try {
            jedis.setex(key, seconds, value);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }
    }

    public static <T extends Model> T get(String key, Class<T> clazz) {
        Jedis jedis = jedisPool.getResource();
        T t = null;

        try {
            String json = jedis.get(key);
            if (json != null)
                t = mapper.readValue(json, clazz);

            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        } catch (IOException e) {
            jedisPool.returnResource(jedis);
            errorlog(e);
        }

        return t;
    }

    public void set(String key) {
        set(key, 3600);
    }

    public void set(String key, int seconds) {
        Jedis jedis = jedisPool.getResource();

        try {
            jedis.setex(key, seconds, this.toString());
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }
    }

    public static void del(String... keys) {
        Jedis jedis = jedisPool.getResource();

        try {
            jedis.del(keys);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }
    }

    public static <T extends Model> T cache(String key, Class<T> clazz, Callable<T> callback) {
        T t = null;

        t = get(key, clazz);
        if (t == null) {
            try {
                t = callback.call();
                if (t != null)
                    t.set(key);
            } catch (Exception e) {
                errorlog(e);
            }
        }

        return t;
    }

    public static void publish(String channel, String message) {
        Jedis jedis = jedisPool.getResource();

        try {
            jedis.publish(channel, message);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }
    }

    public static void subscribe(JedisPubSub pubsub, String... channels) {
        Jedis jedis = jedisPool.getResource();

        while (true) {
            try {
                System.out.println("subscribe...");
                jedis.subscribe(pubsub, channels);
            } catch (JedisConnectionException e) {
                try {
                    jedisPool.returnBrokenResource(jedis);
                    e.printStackTrace();
                    Thread.sleep(3000L);
                    jedis = jedisPool.getResource();
                    System.out.print("re");
                } catch (InterruptedException ie) {
                }
            }
        }
    }

    public String joinObjectId(Collection<ObjectId> ids) {
        StringBuilder out = new StringBuilder();
        for (ObjectId id : ids)
            out.append(id + ",");
        out.delete(out.length() - 1, out.length());

        return out.toString();
    }

    public static String name(ObjectId userId) {
        String key = "name:" + userId;

        String name = get(key);
        if (name != null)
            return name;

        User user = User.get(userId);
        if (user == null)
            return null;

        if (user.getName() != null)
            set(key, user.getName());

        return user.getName();
    }

    public static ObjectId avatar(ObjectId userId) {
        String key = "avatar:" + userId;

        String avatar = get(key);
        if (avatar != null)
            return new ObjectId(avatar);

        User user = User.get(userId);
        if (user == null)
            return null;

        if (user.getAvatar() != null)
            set(key, user.getAvatar().toString());

        return user.getAvatar();
    }

    public static void sendmail(String to, String subject, String body) {
        try {
            SendGrid.Email email = new SendGrid.Email();

            email.setFrom("no-reply@speakaa.com");
            email.addTo(to);
            email.setSubject(subject);
            email.setHtml(body);

            sendgrid.send(email);
        } catch (SendGridException e) {
            throw new RuntimeException(e);
        }
    }

    public String toString() {
        return toJson(this).toString();
    }
}
