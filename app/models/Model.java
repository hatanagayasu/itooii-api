package models;

import play.*;

import java.lang.reflect.Field;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import org.apache.commons.codec.binary.Hex;
import org.bson.types.ObjectId;
import org.jongo.Jongo;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class Model
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

    public static String toJson(Object object)
    {
        StringBuilder result = new StringBuilder(512);

        objectToJson(object, result);

        return result.toString();
    }

    private static void objectToJson(Object object, StringBuilder result)
    {
        try
        {
            result.append("{");

            Field[] fields = object.getClass().getDeclaredFields();
            for (int i = 0; i < fields.length; i++)
            {
                Field field = fields[i];

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
                    result.append("\"").append(name).append("\":[");
                    collectionToJson((Collection)value, result);
                    result.append("]");
                }
                else
                {
                    result.append("\"").append(name).append("\":\"")
                        .append(value).append("\"");
                }

                if (i + 1 < fields.length)
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
}
