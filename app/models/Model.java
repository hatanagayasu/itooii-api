package models;

import play.*;

import java.lang.reflect.Field;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import org.apache.commons.codec.binary.Hex;
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

    public static ObjectNode toJson(Object object)
    {
        ObjectNode json = mapper.createObjectNode();

        try
        {
            for (Field field : object.getClass().getDeclaredFields())
            {
                field.setAccessible(true);
                String name = field.getName();
                Object value = field.get(object);

                if (value == null)
                    continue;

                Class<?> clazz = value.getClass();
                if (clazz == Boolean.class)
                {
                    json.put(name, (Boolean)value);
                }
                else if (clazz == Date.class)
                {
                    Date date = (Date)value;
                    json.put(name, date.getTime() / 1000);
                }
                else if (clazz == Integer.class)
                {
                    json.put(name, (Integer)value);
                }
                else if (clazz == String.class)
                {
                    json.put(name, (String)value);
                }
                else
                {
                    json.put(name, value.toString());
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return json;
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
