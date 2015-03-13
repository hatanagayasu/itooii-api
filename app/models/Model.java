package models;

import play.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import org.apache.commons.codec.binary.Hex;
import org.jongo.Jongo;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class Model implements Serializable
{
    public static ObjectMapper mapper = new ObjectMapper();

    public static DB mongodb;
    public static Jongo jongo;

    private static JedisPool jedisPool;
    private static int redisDB;

    public static void init()
    {
        Configuration conf = Play.application().configuration();

        String mongodbHost = conf.getString("mongodb.host");
        int mongodbPort = conf.getInt("mongodb.port");
        String mongodbDB = conf.getString("mongodb.db");

        String redisHost = conf.getString("redis.host", "localhost");
        int redisPort = conf.getInt("redis.port", 6379);
        redisDB = conf.getInt("redis.db", 0);

        try
        {
            mongodb = new MongoClient(mongodbHost, mongodbPort).getDB(mongodbDB);
            jongo = new Jongo(mongodb);

            jedisPool = new JedisPool(redisHost, redisPort);
        }
        catch (UnknownHostException e)
        {
            //TODO
        }
        catch (JedisConnectionException e)
        {
            //TODO
        }
    }

    public static Jedis getJedis()
    {
        Jedis jedis = jedisPool.getResource();
        jedis.select(redisDB);

        return jedis;
    }

    public static void returnJedis(Jedis jedis)
    {
        jedisPool.returnResource(jedis);
    }

    public static String toJson(Model model)
    {
        StringBuilder result = new StringBuilder(512);

        objectToJson(model, result);

        return result.toString();
    }

    public static String toJson(List<? extends Model> models)
    {
        StringBuilder result = new StringBuilder(512);

        collectionToJson(models, result);

        return result.toString();
    }

    private static void objectToJson(Object object, StringBuilder result)
    {
        try
        {
            result.append("{");

            List<Field>fields = new ArrayList();
            for (Field field : object.getClass().getDeclaredFields())
            {
                field.setAccessible(true);
                if (field.get(object) != null)
                    fields.add(field);
            }

            Iterator<Field> iterator = fields.iterator();
            while(iterator.hasNext())
            {
                Field field = iterator.next();

                field.setAccessible(true);
                String name = field.getName();
                Object value = field.get(object);

                if (name.equals("post_id"))
                    System.out.println(value.getClass().getName());

                if (field.getAnnotation(JsonProperty.class) != null)
                    name = name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();

                if (value == null)
                    continue;

                Class<?> clazz = value.getClass();
                if (clazz == Boolean.class)
                {
                    result.append("\"").append(name).append("\":")
                        .append((Boolean)value);
                }
                else if (clazz == Date.class)
                {
                    Date date = (Date)value;
                    result.append("\"").append(name).append("\":")
                        .append(date.getTime() / 1000);
                }
                else if (clazz == Integer.class)
                {
                    result.append("\"").append(name).append("\":")
                        .append((Integer)value);
                }
                else if (clazz == String.class)
                {
                    result.append("\"").append(name).append("\":\"")
                        .append((String)value).append("\"");
                }
                else if (value instanceof Collection)
                {
                    result.append("\"").append(name).append("\":");
                    collectionToJson((Collection)value, result);
                }
                else if (value instanceof Model)
                {
                    result.append("\"").append(name).append("\":");
                    objectToJson(value, result);
                }
                else
                {
                    result.append("\"").append(name).append("\":\"")
                        .append(value).append("\"");
                }

                if (iterator.hasNext())
                    result.append(",");
            }

            result.append("}");
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void collectionToJson(Collection collection, StringBuilder result)
    {
        result.append("[");

        if (!collection.isEmpty())
        {
            Object object = collection.iterator().next();
            Class clazz = object.getClass();
            if (clazz == Boolean.class)
            {
                Iterator<Boolean> iterator = collection.iterator();
                while (iterator.hasNext())
                {
                    result.append(iterator.next());
                    if (iterator.hasNext())
                        result.append(",");
                }
            }
            else if (clazz == Date.class)
            {
                Iterator<Date> iterator = collection.iterator();
                while (iterator.hasNext())
                {
                    result.append(iterator.next().getTime() / 1000);
                    if (iterator.hasNext())
                        result.append(",");
                }
            }
            else if (clazz == Integer.class)
            {
                Iterator<Integer> iterator = collection.iterator();
                while (iterator.hasNext())
                {
                    result.append(iterator.next());
                    if (iterator.hasNext())
                        result.append(",");
                }
            }
            else if (clazz == String.class)
            {
                Iterator<String> iterator = collection.iterator();
                while (iterator.hasNext())
                {
                    result.append("\"").append(iterator.next()).append("\"");
                    if (iterator.hasNext())
                        result.append(",");
                }
            }
            else if (object instanceof Collection)
            {
                Iterator<Collection> iterator = collection.iterator();
                while (iterator.hasNext())
                {
                    collectionToJson(iterator.next(), result);
                    if (iterator.hasNext())
                        result.append(",");
                }
            }
            else if (object instanceof Model)
            {
                Iterator<Object> iterator = collection.iterator();
                while (iterator.hasNext())
                {
                    objectToJson(iterator.next(), result);
                    if (iterator.hasNext())
                        result.append(",");
                }
            }
            else
            {
                Iterator<Object> iterator = collection.iterator();
                while (iterator.hasNext())
                {
                    result.append("\"").append(iterator.next()).append("\"");
                    if (iterator.hasNext())
                        result.append(",");
                }
            }
        }

        result.append("]");
    }

    public static String md5(String input)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] digest = md.digest();

            return new String(Hex.encodeHex(digest));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static byte[] serialize(Object object)
    {
        try
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(object);

            return baos.toByteArray();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static Object unserialize(byte[] bytes)
    {
        try
        {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bais);

            return ois.readObject();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
