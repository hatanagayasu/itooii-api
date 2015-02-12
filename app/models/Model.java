package models;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.codec.binary.Hex;

public class Model
{
    public static ObjectMapper mapper = new ObjectMapper();

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
