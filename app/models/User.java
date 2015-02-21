package models;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;
import redis.clients.jedis.Jedis;

@lombok.Getter @lombok.Setter
public class User extends Model
{
    @Id
    private String id;
    private String email;
    private String password;
    private String name;
    @JsonProperty("native_language")
    private Set<Integer> nativeLanguage;
    @JsonProperty("practice_language")
    private Set<Integer> practiceLanguage;
    private Set<String> following;
    private Set<String> followers;
    private Date created;
    private Date modified;

    public User()
    {
    }

    public User(JsonNode params)
    {
        Date now = new Date();

        id = UUID.randomUUID().toString();
        email = params.get("email").textValue();
        password = md5(params.get("password").textValue());

        Iterator<JsonNode> values = params.get("native_language").iterator();
        nativeLanguage = new HashSet();
        while (values.hasNext())
            nativeLanguage.add(values.next().intValue());

        values = params.get("practice_language").iterator();
        practiceLanguage = new HashSet();
        while (values.hasNext())
            practiceLanguage.add(values.next().intValue());

        created = now;
        modified = now;
    }

    public static User add(JsonNode params)
    {
        User user = new User(params);

        MongoCollection userCol = jongo.getCollection("user");
        userCol.save(user);

        user.setPassword(null);

        return user;
    }

    public void update(JsonNode params)
    {
        update((ObjectNode)params);
    }

    private void update(ObjectNode params)
    {
        MongoCollection userCol = jongo.getCollection("user");

        params.remove("access_token");
        params.putPOJO("modified", new Date());

        if (params.has("password"))
        {
            String password = params.get("password").textValue();
            params.put("password", md5(password));
        }

        userCol.update("{_id:#}", id).with("{$set:#}", params);
    }

    public void follow(String userId)
    {
        MongoCollection userCol = jongo.getCollection("user");

        userCol.update("{_id:#}", userId).with("{$addToSet:{followers:#}}", id);
        userCol.update("{_id:#}", id).with("{$addToSet:{following:#}}", userId);
    }

    public void unfollow(String userId)
    {
        MongoCollection userCol = jongo.getCollection("user");

        userCol.update("{_id:#}", id).with("{$pull:{following:#}}", userId);
        userCol.update("{_id:#}", userId).with("{$pull:{followers:#}}", id);
    }

    public static User getById(String userId)
    {
        MongoCollection userCol = jongo.getCollection("user");

        User user = userCol.findOne("{_id:#}", userId).as(User.class);

        return user;
    }

    public static User getByEmail(String email)
    {
        MongoCollection userCol = jongo.getCollection("user");

        User user = userCol.findOne("{email:#}", email).as(User.class);

        return user;
    }

    public static User getByToken(String token)
    {
        Jedis jedis = getJedis();
        String email = jedis.get("token:" + token);
        returnJedis(jedis);

        if(email == null)
            return null;

        return getByEmail(email);
    }

    public boolean matchPassword(String password)
    {
        return this.password.equals(md5(password));
    }

    public static boolean checkToken(String token)
    {
        Jedis jedis = getJedis();
        boolean exists = jedis.exists("token:" + token);
        returnJedis(jedis);

        return exists;
    }

    public String newToken()
    {
        String token = UUID.randomUUID().toString();
        Jedis jedis =  getJedis();
        jedis.setex("token:" + token, 86400, email);
        returnJedis(jedis);

        return token;
    }

    public static void deleteToken(String token)
    {
        Jedis jedis =  getJedis();
        jedis.del("token:" + token);
        returnJedis(jedis);
    }
}
