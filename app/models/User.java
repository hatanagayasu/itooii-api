package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class User extends Other {
    private String email;
    private String password;

    public User() {
    }

    public User(String email, String password, String name, List<Integer> nativeLanguage,
        List<PracticeLanguage> practiceLanguage) {
        id = new ObjectId();
        this.email = email;
        this.password = md5(password);
        this.name = name;
        this.nativeLanguage = nativeLanguage;
        this.practiceLanguage = practiceLanguage;
        created = new Date();
    }

    public void save() {
        MongoCollection userCol = jongo.getCollection("user");
        userCol.save(this);
        password = null;
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

        del("user:" + id);
    }

    public void follow(ObjectId userId) {
        MongoCollection following = jongo.getCollection("following");
        MongoCollection follower = jongo.getCollection("follower");

        following.save(new Following(id, userId));
        follower.save(new Follower(userId, id));

        del("user:" + id, "user:" + userId);
    }

    public void unfollow(ObjectId userId) {
        MongoCollection following = jongo.getCollection("following");
        MongoCollection follower = jongo.getCollection("follower");

        following.remove("{user_id:#,following_id:#}", id, userId);
        follower.remove("{user_id:#,follower_id:#}", userId, id);

        del("user:" + id, "user:" + userId);
    }

    public static Page search(int skip, int limit) {
        MongoCollection userCol = jongo.getCollection("user");
        String next = null;

        MongoCursor<Other> cursor = userCol.find()
            .skip(skip)
            .limit(limit)
            .as(Other.class);
        List<Other> others = new ArrayList<Other>();
        while (cursor.hasNext())
            others.add(cursor.next());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (others.size() == limit)
            next = String.format("skip=%d&limit=%d", skip + limit, limit);

        return new Page(others, next);
    }

    public static User get(ObjectId userId) {
        String key = "user:" + userId;

        return cache(key, User.class, new Callable<User>() {
            public User call() {
                MongoCollection userCol = jongo.getCollection("user");
                User user = userCol.findOne(userId).projection("{password:0}").as(User.class);

                if (user != null) {
                    user.followings = Following.getFollowingIds(userId);
                    user.followers = Follower.getFollowerIds(userId);
                }

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
        String id = get("token:" + token);

        if (id == null)
            return null;

        return get(new ObjectId(id));
    }

    public static String getUserIdByToken(String token) {
        String id = get("token:" + token);

        return id;
    }

    public String newToken() {
        String token = UUID.randomUUID().toString();
        set("token:" + token, 86400, id.toString());

        return token;
    }

    public static void newToken(String userId, String token) {
        set("token:" + token, 86400, userId);
    }

    public static void deleteToken(String token) {
        del("token:" + token);
    }
}
