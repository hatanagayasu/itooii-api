package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;

@lombok.Getter
public class User extends Other {
    private String email;
    @JsonProperty("email_verified")
    private boolean emailVerified;
    private String password;
    private Set<ObjectId> blockings;
    private int privilege;
    private int tos;

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
        privilege = Privilege.Observer.value();
        tos = 1;
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

        if (privilege == Privilege.Observer.value() &&
            emailVerified == true &&
            (birthday != null || params.has("birthday")) &&
            (gender != 0 || params.has("gender")) &&
            (nationality != null || params.has("nationality")) &&
            (country != null || params.has("country")) &&
            (city != null || params.has("city")) &&
            (avatar != null || params.has("avatar")))
            params.put("privilege", Privilege.Member.value());

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

    public void blocking(ObjectId userId) {
        MongoCollection userCol = jongo.getCollection("user");
        MongoCollection following = jongo.getCollection("following");
        MongoCollection follower = jongo.getCollection("follower");

        userCol.update(id).with("{$addToSet:{blockings:#}}", userId);

        following.remove("{user_id:#,following_id:#}", id, userId);
        following.remove("{user_id:#,following_id:#}", userId, id);

        follower.remove("{user_id:#,follower_id:#}", id, userId);
        follower.remove("{user_id:#,follower_id:#}", userId, id);

        del("user:" + id, "user:" + userId);
    }

    public void unblocking(ObjectId userId) {
        MongoCollection userCol = jongo.getCollection("user");

        userCol.update(id).with("{$pull:{blockings:#}}", userId);

        del("user:" + id);
    }

    public static User verifyEmail(String token) {
        MongoCollection userCol = jongo.getCollection("user");

        User user = userCol.findAndModify("{email_verified_token:#}", token)
            .with("{$set:{email_verified:true},$unset:{email_verified_token:0}}")
            .projection("{_id:1}")
            .as(User.class);

        if (user != null)
            del("user:" + user.id);

        return user;
    }

    public String reverifyEmail() {
        MongoCollection userCol = jongo.getCollection("user");

        String token = UUID.randomUUID().toString();
        userCol.update(id).with("{$set:{email_verified:false,email_verified_token:#}}", token);

        return token;
    }

    public Page getFollower(int skip, int limit) {
        return page(followers, skip, limit, Skim.class);
    }

    public Page getFollowing(int skip, int limit) {
        return page(followings, skip, limit, Skim.class);
    }

    public Page getBlocking(int skip, int limit) {
        return page(blockings, skip, limit, Skim.class);
    }

    public static Page search(int skip, int limit) {
        MongoCollection userCol = jongo.getCollection("user");
        String next = null;

        MongoCursor<Skim> cursor = userCol.find()
            .skip(skip)
            .limit(limit)
            .as(Skim.class);
        List<Skim> skims = new ArrayList<Skim>();
        while (cursor.hasNext())
            skims.add(cursor.next());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (skims.size() == limit)
            next = String.format("skip=%d&limit=%d", skip + limit, limit);

        return new Page(skims, next);
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
