package models;

import controllers.exceptions.ObjectForbiddenException;

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
    private int tos;

    public User() {
    }

    public User(String email, String password, String name, List<Integer> nativeLanguage,
        List<PracticeLanguage> practiceLanguage) {
        id = new ObjectId();
        this.email = email.toLowerCase();
        this.password = md5(password);
        this.name = name;
        this.nativeLanguage = nativeLanguage;
        this.practiceLanguage = practiceLanguage;
        created = new Date();
        activity = new Date();
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

        if (params.size() > 0)
            userCol.update(id).with("{$set:#}", params);

        del(id);
    }

    public void updateAvatar(ObjectId avatar) {
        MongoCollection userCol = jongo.getCollection("user");
        MongoCollection mediacol = jongo.getCollection("media");

        Media media = mediacol.findAndModify("{_id:#,user_id:#,'type':'photo'}", avatar, id)
            .with("{$unset:{posted:0}}")
            .projection("{_id:1}")
            .as(Media.class);

        if (media == null)
            throw new RuntimeException(new ObjectForbiddenException());

        ObjectNode params = mapper.createObjectNode();
        params.putPOJO("avatar", avatar);

        update(params);
    }

    public void follow(ObjectId userId) {
        MongoCollection following = jongo.getCollection("following");
        MongoCollection follower = jongo.getCollection("follower");

        following.save(new Following(id, userId));
        follower.save(new Follower(userId, id));

        del(id, userId);

        List<Attachment> attachments = new ArrayList<Attachment>(1);
        attachments.add(new Attachment(AttachmentType.follow, userId));
        Post post = new Post(id, null, attachments, true);
        post.save();

        if (follower != null)
            new Activity(id, ActivityType.follow, post.getId(), followers).queue();
    }

    public void unfollow(ObjectId userId) {
        MongoCollection following = jongo.getCollection("following");
        MongoCollection follower = jongo.getCollection("follower");

        following.remove("{user_id:#,following_id:#}", id, userId);
        follower.remove("{user_id:#,follower_id:#}", userId, id);

        del(id, userId);
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

        del(id, userId);
    }

    public void unblocking(ObjectId userId) {
        MongoCollection userCol = jongo.getCollection("user");

        userCol.update(id).with("{$pull:{blockings:#}}", userId);

        del(id);
    }

    public static User verifyEmail(String token) {
        MongoCollection userCol = jongo.getCollection("user");

        User user = userCol.findAndModify("{email_verified_token:#}", token)
            .with("{$set:{email_verified:true},$unset:{email_verified_token:0}}")
            .projection("{password:0}")
            .as(User.class);

        if (user != null) {
            ObjectNode params = mapper.createObjectNode();
            user.update(params);
        }

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

    public static Page search(JsonNode params, long until, int limit) {
        MongoCollection userCol = jongo.getCollection("user");
        String previous = null;

        String query = "activity:{$lt:#}";

        if (params.has("native_language"))
            query += ",native_language:" + params.get("native_language").intValue();
        if (params.has("practice_language"))
            query += ",practice_language:{$elemMatch:{id:" +
                params.get("practice_language").intValue() + "}}";
        if (params.has("gender"))
            query += ",gender:" + params.get("gender").intValue();

        MongoCursor<Skim> cursor = userCol.find("{" + query + "}", new Date(until))
            .sort("{activity:-1}")
            .limit(limit)
            .as(Skim.class);
        List<Skim> skims = new ArrayList<Skim>();
        Skim skim = null;
        while (cursor.hasNext()) {
            skim = cursor.next();
            if (skim.privilege > Privilege.Observer.value())
                skims.add(skim);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (skims.size() == limit) {
            until = skim.activity.getTime();
            previous = String.format("until=%d&limit=%d", until, limit);
        }

        return new Page(skims, previous);
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

        User user = userCol.findOne("{email:#}", email.toLowerCase())
            .projection("{password:1}").as(User.class);

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
        MongoCollection col = jongo.getCollection("user");

        col.update(userId).with("{$set:{activity:#}}", new Date());

        set("token:" + token, 86400, userId);
    }

    public static void deleteToken(String token) {
        del("token:" + token);
    }

    private static void del(ObjectId id) {
        del("user:" + id, "name:" + id, "avatar:" + id);
    }

    private static void del(ObjectId id1, ObjectId id2) {
        del("user:" + id1, "name:" + id1, "avatar:" + id1,
            "user:" + id2, "name:" + id1, "avatar:" + id1);
    }
}
