package models;

import play.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
import redis.clients.jedis.Tuple;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class Model {
    public static Properties props;
    public static ObjectMapper mapper = new ObjectMapper();
    private static JsonStringEncoder encoder = JsonStringEncoder.getInstance();

    public static DB mongodb;
    public static Jongo jongo;

    private static JedisPool jedisPool;
    private static Map<String, String> luasha1 =
        Collections.synchronizedMap(new HashMap<String, String>());

    private static SendGrid sendgrid;

    public static ObjectId epochId = new ObjectId(new Date(0));

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            props = new Properties();
            props.load(new FileInputStream("conf/application.conf"));

            String mongodbHost = props.getProperty("mongodb.host", "localhost");
            int mongodbPort = Integer.parseInt(props.getProperty("mongodb.port", "27017"));
            String mongodbDB = props.getProperty("mongodb.db", "itooii");

            String redisHost = props.getProperty("redis.host", "localhost");
            int redisPort = Integer.parseInt(props.getProperty("redis.port", "6379"));

            sendgrid = new SendGrid(props.getProperty("sendgrid.api_key"));

            mongodb = new MongoClient(mongodbHost, mongodbPort).getDB(mongodbDB);
            jongo = new Jongo(mongodb);

            jedisPool = new JedisPool(redisHost, redisPort);
        } catch (UnknownHostException e) {
            //TODO
        } catch (JedisConnectionException e) {
            //TODO
        } catch (IOException e) {
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
                } else if (value instanceof JsonNode) {
                    result.append("\"").append(name).append("\":").append(value);
                } else {
                    result.append("\"").append(name).append("\":\"").append(value).append("\"");

                    Postproduct postproduct = field.getAnnotation(Postproduct.class);
                    if (postproduct != null) {
                        if (postproduct.value().equals("event")) {
                            Event event = Event.get((ObjectId)value);
                            result.append(",\"").append("event_name").append("\":\"")
                                .append(event.getName()).append("\"")
                                .append(",\"").append("event_alias").append("\":\"")
                                .append(event.getAlias()).append("\"");
                        } else {
                            result.append(",\"").append((postproduct.value().length() > 0 ?
                                    postproduct.value() + "_name" : "name")).append("\":\"")
                                .append(name((ObjectId)value)).append("\"");

                            ObjectId avatar = avatar((ObjectId)value);
                            if (avatar != null) {
                                result.append(",\"").append((postproduct.value().length() > 0 ?
                                        postproduct.value() + "_avatar" : "avatar")).append("\":\"")
                                    .append(avatar).append("\"");
                            }
                        }
                    }
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

    public static Object evalScript(String name, String... params) {
        Jedis jedis = jedisPool.getResource();
        Object result = null;

        try {
            String sha1 = luasha1.get(name);
            if (sha1 == null || !jedis.scriptExists(sha1)) {
                String path = Play.application().path() + "/scripts/lua/";
                String script = new String(Files.readAllBytes(Paths.get(path + name + ".lua")));

                sha1 = jedis.scriptLoad(script);
                luasha1.put(name, sha1);
            }

            result = jedis.evalsha(sha1, 0, params);

            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException | IOException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static boolean exists(String key) {
        Jedis jedis = jedisPool.getResource();
        boolean result = false;

        try {
            result = jedis.exists(key);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
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

    public static String getex(String key, int expire) {
        Jedis jedis = jedisPool.getResource();
        String result = null;

        try {
            result = jedis.get(key);
            jedis.expire(key, expire);

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

    public static String hget(String key, String field) {
        Jedis jedis = jedisPool.getResource();
        String result = null;

        try {
            result = jedis.hget(key, field);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static long sadd(String key, String... members) {
        Jedis jedis = jedisPool.getResource();
        long result = 0;

        try {
            result = jedis.sadd(key, members);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static long srem(String key, String... members) {
        Jedis jedis = jedisPool.getResource();
        long result = 0;

        try {
            result = jedis.srem(key, members);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static boolean sismember(String key, String member) {
        Jedis jedis = jedisPool.getResource();
        boolean result = false;

        try {
            result = jedis.sismember(key, member);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }
    public static Set<String> smembers(String key) {
        Jedis jedis = jedisPool.getResource();
        Set<String> result = null;

        try {
            result = jedis.smembers(key);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static long zadd(String key, double score, String member) {
        Jedis jedis = jedisPool.getResource();
        long result = 0;

        try {
            result = jedis.zadd(key, score, member);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static long zadd(String key, Map<String, Double> scoreMembers) {
        Jedis jedis = jedisPool.getResource();
        long result = 0;

        try {
            result = jedis.zadd(key, scoreMembers);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static long zrem(String key, String... members) {
        Jedis jedis = jedisPool.getResource();
        long result = 0;

        try {
            result = jedis.zrem(key, members);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static long zinterstore(String dstkey, String... sets) {
        Jedis jedis = jedisPool.getResource();
        Long result = null;

        try {
            result = jedis.zinterstore(dstkey, sets);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static Long zrank(String key, String member) {
        Jedis jedis = jedisPool.getResource();
        Long result = null;

        try {
            result = jedis.zrank(key, member);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
    }

    public static Set<Tuple> zrevrangeByScoreWithScores(String key, double min, double max,
        int offset, int count) {
        Jedis jedis = jedisPool.getResource();
        Set<Tuple> result = null;

        try {
            result = jedis.zrevrangeByScoreWithScores(key, min, max, offset, count);
            jedisPool.returnResource(jedis);
        } catch (JedisConnectionException e) {
            jedisPool.returnBrokenResource(jedis);
            errorlog(e);
        }

        return result;
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

    public static String name(ObjectId userId) {
        if (userId == null)
            return null;

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
        if (userId == null)
            return null;

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

    public static long now() {
        return System.currentTimeMillis();
    }

    public String toString() {
        return toJson(this).toString();
    }
}
