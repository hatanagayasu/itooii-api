package models;

import models.Following;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;
import redis.clients.jedis.Jedis;

@lombok.Getter
public class User extends Model
{
    @Id
    private ObjectId id;
    private String email;
    private String password;
    private String name;
    @JsonProperty("native_language")
    private Set<Integer> nativeLanguage;
    @JsonProperty("practice_language")
    private Set<Integer> practiceLanguage;
    private Set<Following> following;
    private Date created;

    public User()
    {
    }

    public User(JsonNode params)
    {
        Date now = new Date();

        id = new ObjectId();
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
    }

    public static User add(JsonNode params)
    {
        User user = new User(params);

        MongoCollection userCol = jongo.getCollection("user");
        userCol.save(user);

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

        if (params.has("password"))
        {
            String password = params.get("password").textValue();
            params.put("password", md5(password));
        }

        userCol.update(id).with("{$set:#}", params);
    }

    public void follow(ObjectId userId)
    {
        MongoCollection user = jongo.getCollection("user");

        user.update("{_id:#,following.id:{$ne:#}}", id, userId)
            .with("{$addToSet:{following:#}}", new Following(userId));
    }

    public void unfollow(ObjectId userId)
    {
        MongoCollection user = jongo.getCollection("user");

        user.update(id).with("{$pull:{following:{id:#}}}", userId);
    }

    public boolean containsFollowing(ObjectId userId)
    {
        if (this.following != null)
        {
            Iterator<Following> following = this.following.iterator();
            while (following.hasNext())
            {
                if (userId.equals(following.next().getId()))
                    return true;
            }
        }

        return false;
    }

    public void removePassword()
    {
        password = null;
    }

    public static User getById(ObjectId userId)
    {
        MongoCollection userCol = jongo.getCollection("user");

        User user = userCol.findOne(userId).as(User.class);

        return user;
    }

    public static User getByEmail(String email)
    {
        MongoCollection userCol = jongo.getCollection("user");

        User user = userCol.findOne("{email:#}", email).projection("{password:1}").as(User.class);

        return user;
    }

    public static User getByToken(String token)
    {
        Jedis jedis = getJedis();
        String id = jedis.get("token:" + token);
        returnJedis(jedis);

        if(id == null)
            return null;

        return getById(new ObjectId(id));
    }

    public static String getUserIdByToken(String token)
    {
        Jedis jedis = getJedis();
        String id = jedis.get("token:" + token);
        returnJedis(jedis);

        return id;
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
        Jedis jedis = getJedis();
        jedis.setex("token:" + token, 86400, id.toString());
        returnJedis(jedis);

        return token;
    }

    public static void deleteToken(String token)
    {
        Jedis jedis = getJedis();
        jedis.del("token:" + token);
        returnJedis(jedis);
    }

    public static void online(String userId, String token, String host)
    {
        Jedis jedis = getJedis();
        jedis.setex("token:" + token, 86400, userId);
        jedis.hsetnx("host:" + userId, token, host);
        returnJedis(jedis);
    }

    public static void offline(String userId, String token)
    {
        Jedis jedis = getJedis();
        jedis.del("token:" + token);
        jedis.hdel("host:" + userId, token);
        returnJedis(jedis);
    }

    public static Map<String,String> getTokenHosts(ObjectId userId)
    {
        Jedis jedis = getJedis();
        Map result = jedis.hgetAll("host:" + userId.toString());
        returnJedis(jedis);

        return result;
    }
}
