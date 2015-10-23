package models;

import controllers.exceptions.ObjectForbiddenException;

import play.libs.ws.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
    @JsonProperty("last_read_notification")
    private ObjectId lastReadNotification;
    private Set<Gcm> gcms;

    public User() {
    }

    public User(String email, String password, String name, String nationality, int gender,
                    String birthday, List<Integer> nativeLanguage,
        List<PracticeLanguage> practiceLanguage) {
        id = new ObjectId();
        this.email = email.toLowerCase();
        this.password = md5(password);
        this.name = name;
        this.nationality = nationality;
        this.gender = gender;
        this.birthday = birthday;
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

        if (privilege == Privilege.Observer.value() && emailVerified == true)
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

        if (followers != null) {
            List<Attachment> attachments = new ArrayList<Attachment>(1);
            attachments.add(new Attachment(AttachmentType.change_profile_photo, avatar));
            Post post = new Post(id, null, attachments, true);
            post.save();

            new Activity(id, ActivityType.changeProfilePhoto, post.getId(), followers).queue();
        }
    }

    public void updateLastReadNotificaotion(ObjectId id) {
        MongoCollection userCol = jongo.getCollection("user");

        userCol.update(this.id).with("{$set:{last_read_notification:#}}", id);

        del(this.id);
    }

    public void follow(ObjectId userId) {
        MongoCollection following = jongo.getCollection("following");
        MongoCollection follower = jongo.getCollection("follower");

        List<Attachment> attachments = new ArrayList<Attachment>(1);
        attachments.add(new Attachment(AttachmentType.follow, userId));
        Post post = new Post(id, null, attachments, true);
        post.save();

        if (followers != null) {
            followers.remove(userId);
            new Activity(id, ActivityType.follow, post.getId(), followers).queue();
        }

        new Activity(id, ActivityType.followYou, post.getId(), userId).queue();

        following.save(new Following(id, userId));
        follower.save(new Follower(userId, id));

        del(id, userId);
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

        User user = userCol
            .findAndModify("{tokens:{$elemMatch:{token:#,modified:{$exists:false}}}}", token)
            .with("{$set:{email_verified:true,'tokens.$.modified':#}}", new Date())
            .projection("{password:0}")
            .as(User.class);

        if (user != null) {
            user.emailVerified = true;
            ObjectNode params = mapper.createObjectNode();
            user.update(params);
        }

        return user;
    }

    public String reverifyEmail() {
        MongoCollection userCol = jongo.getCollection("user");

        String token = UUID.randomUUID().toString();
        userCol.update(id)
            .with("{$push:{tokens:{token:#,created:#}}}", token, new Date());

        return token;
    }

    public static User getByToken(String token) {
        MongoCollection userCol = jongo.getCollection("user");

        User user = userCol
            .findAndModify("{tokens:{$elemMatch:{token:#,modified:{$exists:false}}}}", token)
            .with("{$set:{'tokens.$.modified':#}}", new Date())
            .projection("{_id:1}")
            .as(User.class);

        return user;
    }

    public static String forgetPassword(String email) {
        MongoCollection userCol = jongo.getCollection("user");

        String token = UUID.randomUUID().toString();
        User user = userCol
            .findAndModify("{email:#}", email.toLowerCase())
            .with("{$push:{tokens:{token:#,created:#}}}", token, new Date())
            .projection("{_id:1}")
            .as(User.class);

        return user == null ? null : token;
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

    public static Page search(JsonNode params, Date until, int limit) {
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

        MongoCursor<Skim> cursor = userCol.find("{" + query + "}", until)
            .sort("{activity:-1}")
            .limit(limit)
            .as(Skim.class);

        List<Skim> skims = new ArrayList<Skim>();
        Skim skim = null;
        int count = 0;
        while (cursor.hasNext()) {
            skim = cursor.next();
            count++;
            if (skim.privilege > Privilege.Observer.value())
                skims.add(skim);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (count == limit)
            previous = String.format("until=%d&limit=%d", skim.activity.getTime(), limit);

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

    public static User getByAccessToken(String token) {
        String id = get("token:" + token);

        if (id == null)
            return null;

        return get(new ObjectId(id));
    }

    public static String getUserIdByAccessToken(String token) {
        String id = get("token:" + token);

        return id;
    }

    public String newAccessToken() {
        String token = UUID.randomUUID().toString();
        set("token:" + token, 86400, id.toString());

        return token;
    }

    public static void newAccessToken(String userId, String token) {
        MongoCollection col = jongo.getCollection("user");

        col.update(new ObjectId(userId)).with("{$set:{activity:#}}", new Date());

        set("token:" + token, 86400, userId);
    }

    public static void deleteAccessToken(String token) {
        del("token:" + token);
    }

    private static void del(ObjectId id) {
        del("user:" + id, "name:" + id, "avatar:" + id);
    }

    private static void del(ObjectId id1, ObjectId id2) {
        del("user:" + id1, "name:" + id1, "avatar:" + id1,
            "user:" + id2, "name:" + id1, "avatar:" + id1);
    }

    public void addGCM(String id, String lang) {
        MongoCollection col = jongo.getCollection("user");

        col.update(this.id).with("{$push:{gcms:{id:#,lang:#}}}", id, lang);

        del(this.id);
    }

    public void updateGCM(String id, String lang) {
        MongoCollection col = jongo.getCollection("user");

        col.update("{_id:#,gcms:{$elemMatch:{id:#}}}", this.id, id)
            .with("{$set:{'gcms.$.lang':#}}", lang);

        del(this.id);
    }

    public void removeGCM(String id) {
        MongoCollection col = jongo.getCollection("user");

        col.update(this.id).with("{$pull:{gcms:{id:#}}}", id);

        del(this.id);
    }

    public void pushNotification() {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode ids = json.putArray("registration_ids");
        for (Gcm gcm : gcms)
            ids.add(gcm.getId());
        ObjectNode data = json.putObject("data");
        data.put("message", "something new?");

        WS.url("https://android.googleapis.com/gcm/send")
            .setHeader("Authorization", "key=AIzaSyCm1b2WpHdcWf49ese9Bf8ZMnGqyTG1Bsw")
            .setHeader("Content-Type", "application/json")
            .post(json);
    }
}
