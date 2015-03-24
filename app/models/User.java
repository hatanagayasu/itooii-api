package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;
import redis.clients.jedis.Jedis;

@lombok.Getter
public class User extends Model {
    @Id
    private ObjectId id;
    private String email;
    private String password;
    private String name;
    @JsonProperty("native_language")
    private Set<Integer> nativeLanguage;
    @JsonProperty("practice_language")
    private Set<PracticeLanguage> practiceLanguage;
    private Date created;
    @JsonIgnore
    private Set<ObjectId> followings;
    @JsonIgnore
    private Set<ObjectId> followers;

    public User() {
    }

    public User(String email, String password, Set<Integer> nativeLanguage,
                    Set<PracticeLanguage> practiceLanguage) {
        id = new ObjectId();
        this.email = email;
        this.password = md5(password);
        this.nativeLanguage = nativeLanguage;
        this.practiceLanguage = practiceLanguage;
        created = new Date();
    }

    public void save() {
        MongoCollection userCol = jongo.getCollection("user");
        userCol.save(this);
    }

    public void update(JsonNode params) {
        update((ObjectNode) params);
    }

    private void update(ObjectNode params) {
        MongoCollection userCol = jongo.getCollection("user");

        params.remove("access_token");

        if (params.has("password")) {
            String password = params.get("password").textValue();
            params.put("password", md5(password));
        }

        userCol.update(id).with("{$set:#}", params);

        expire("user:" + id);
    }

    public void follow(ObjectId userId) {
        MongoCollection following = jongo.getCollection("following");
        MongoCollection follower = jongo.getCollection("follower");

        following.save(new Following(id, userId));
        follower.save(new Follower(userId, id));

        expire(new String[] { "user:" + id, "user:" + userId });
    }

    public void unfollow(ObjectId userId) {
        MongoCollection following = jongo.getCollection("following");
        MongoCollection follower = jongo.getCollection("follower");

        following.remove("{user_id:#,following_id:#}", id, userId);
        follower.remove("{user_id:#,follower_id:#}", userId, id);

        expire(new String[] { "user:" + id, "user:" + userId });
    }

    public static List<User> search() {
        MongoCollection userCol = jongo.getCollection("user");

        MongoCursor<User> cursor = userCol.find().projection("{_id:1}").as(User.class);
        List<User> users = new ArrayList<User>();
        while (cursor.hasNext())
            users.add(cursor.next());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return users;
    }

    public void removePassword() {
        password = null;
    }

    public static User getById(ObjectId userId) {
        String key = "user:" + userId;

        return cache(key, new Callable<User>() {
            public User call() {
                MongoCollection userCol = jongo.getCollection("user");
                User user = userCol.findOne(userId).as(User.class);

                if (user.name == null)
                    user.name = user.email.replaceFirst("@.*", "");

                user.followings = Following.getFollowingIds(userId);
                user.followers = Follower.getFollowerIds(userId);

                return user;
            }
        });
    }

    public static User getByEmail(String email) {
        MongoCollection userCol = jongo.getCollection("user");

        User user = userCol.findOne("{email:#}", email).projection("{password:1}").as(User.class);

        return user;
    }

    public static User getByToken(String token) {
        Jedis jedis = getJedis();
        String id = jedis.get("token:" + token);
        returnJedis(jedis);

        if (id == null)
            return null;

        return getById(new ObjectId(id));
    }

    public static String getUserIdByToken(String token) {
        Jedis jedis = getJedis();
        String id = jedis.get("token:" + token);
        returnJedis(jedis);

        return id;
    }

    public static boolean checkToken(String token) {
        Jedis jedis = getJedis();
        boolean exists = jedis.exists("token:" + token);
        returnJedis(jedis);

        return exists;
    }

    public String newToken() {
        String token = UUID.randomUUID().toString();
        Jedis jedis = getJedis();
        jedis.setex("token:" + token, 86400, id.toString());
        returnJedis(jedis);

        return token;
    }

    public static void deleteToken(String token) {
        Jedis jedis = getJedis();
        jedis.del("token:" + token);
        returnJedis(jedis);
    }

    public static void online(String userId, String token, String host) {
        Jedis jedis = getJedis();
        jedis.setex("token:" + token, 86400, userId);
        jedis.hsetnx("host:" + userId, token, host);
        returnJedis(jedis);
    }

    public static void offline(String userId, String token) {
        Jedis jedis = getJedis();
        jedis.del("token:" + token);
        jedis.hdel("host:" + userId, token);
        returnJedis(jedis);
    }

    public static Map<String, String> getTokenHosts(ObjectId userId) {
        Jedis jedis = getJedis();
        Map result = jedis.hgetAll("host:" + userId.toString());
        returnJedis(jedis);

        return result;
    }
}
