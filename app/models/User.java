package models;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;
import redis.clients.jedis.Jedis;

@lombok.Getter @lombok.Setter
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
    @JsonIgnore
    private Set<ObjectId> following;
    @JsonIgnore
    private Set<ObjectId> followers;
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

        if (params.has("password"))
        {
            String password = params.get("password").textValue();
            params.put("password", md5(password));
        }

        userCol.update(id).with("{$set:#}", params);
    }

    public void follow(ObjectId userId)
    {
        MongoCollection following = jongo.getCollection("following");
        MongoCollection follower = jongo.getCollection("follower");

        Date now = new Date();
        following.insert("{user_id:#,following:#,created:#}", id, userId, now);
        follower.insert("{user_id:#,follower:#,created:#}", userId, id, now);
    }

    public void unfollow(ObjectId userId)
    {
        MongoCollection following = jongo.getCollection("following");
        MongoCollection follower = jongo.getCollection("follower");

        following.remove("{user_id:#,following:#}", id, userId);
        follower.remove("{user_id:#,follower:#}", userId, id);
    }

    public static User getById(ObjectId userId)
    {
        MongoCollection userCol = jongo.getCollection("user");
        MongoCollection followingCol = jongo.getCollection("following");
        MongoCollection followerCol = jongo.getCollection("follower");

        User user = userCol.findOne(userId).as(User.class);
        if (user == null)
            return null;

        user.following = new HashSet();
        MongoCursor<Following> following = followingCol.find("{user_id:#}", user.id)
            .projection("{following:1}").as(Following.class);
        while(following.hasNext())
            user.following.add(following.next().getFollowing());

        user.followers = new HashSet();
        MongoCursor<Follower> followers = followerCol.find("{user_id:#}", user.id)
            .projection("{follower:1}").as(Follower.class);
        while(followers.hasNext())
            user.followers.add(followers.next().getFollower());

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
        jedis.setex("token:" + token, 86400, id.toString());
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
