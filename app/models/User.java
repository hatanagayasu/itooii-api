package models;

import controllers.exceptions.ObjectForbiddenException;

import play.libs.ws.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

import redis.clients.jedis.Tuple;

@lombok.Getter
public class User extends Other {
    private String email;
    @JsonProperty("email_verified")
    private boolean emailVerified;
    private String password;
    private Set<ObjectId> blockings;
    @JsonProperty("friend_requests")
    private Set<ObjectId> friendRequests;
    private int tos;
    @JsonProperty("last_read_notification")
    private ObjectId lastReadNotification;
    private Set<Gcm> gcms;
    private boolean invisibility;

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

    public Set<ObjectId> getSubscribers() {
        Set<ObjectId> subscribers = new HashSet<ObjectId>();

        if (friends != null)
            subscribers.addAll(friends);
        if (unfollowers != null)
            subscribers.removeAll(unfollowers);
        if (followers != null)
            subscribers.addAll(followers);

        return subscribers;
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

        if (params.has("invisibility")) {
            ObjectNode result = mapper.createObjectNode();
            result.put("action", params.get("invisibility").booleanValue() ? "offline" : "online")
                .put("user_id", id.toString())
                .put("name", name);
            if (avatar != null)
                result.put("avatar", avatar.toString());

            if (friends != null)
                publish("user", friends + "\n" + result);
        }

        del(id);
    }

    public void updateAvatar(ObjectId avatar) {
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

    // 0: send, 1: receive, 2: accept, 3: ignore
    public List<Friend> getFriendRequest(int status) {
        MongoCollection col = jongo.getCollection("friend");

        MongoCursor<Friend> cursor = col.find("{user_id:#,status:#}", id, status)
            .projection("{friend_id:-1}")
            .as(Friend.class);

        List<Friend> friends = new ArrayList<Friend>();
        while (cursor.hasNext())
                friends.add(cursor.next());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return friends;
    }

    public boolean friendRequest(ObjectId userId) {
        //TODO cache
        MongoCollection col = jongo.getCollection("friend");

        Friend friend = col.findOne("{user_id:#,friend_id:#,status:#}", id, userId, 0)
            .projection("{_id:1}")
            .as(Friend.class);

        return friend == null;
    }

    public void sendFriendRequest(ObjectId userId) {
        MongoCollection col = jongo.getCollection("friend");
        Date date = new Date();

        Friend friend = col.findAndModify("{user_id:#,friend_id:#,status:#}", id, userId, 0)
            .with("{$setOnInsert:{created:#}}", date)
            .upsert()
            .projection("{_id:1}")
            .as(Friend.class);

        if (friend == null) {
            del(id);

            col.update("{user_id:#,friend_id:#,status:#}", userId, id, 1)
                .upsert()
                .with("{$setOnInsert:{created:#}}", date);

            new Activity(id, ActivityType.friendRequest, null, userId).queue();
        }
    }

    public void acceptFriendRequest(User user) {
        MongoCollection col = jongo.getCollection("friend");
        Date date = new Date();

        Friend friend = col.findAndModify("{user_id:#,friend_id:#,status:#}", id, user.getId(), 1)
            .with("{$set:{status:#,modified':#}}", 2, date)
            .projection("{_id:1}")
            .as(Friend.class);

        if (friend != null) {
            col.update("{user_id:#,friend_id:#,status:#}", user.getId(), id, 0)
                .with("{$set:{status:#,modified':#}}", 2, date);

            Activity.remove(user.getId(), ActivityType.friendRequest, id);

            unfollow(user.getId());
            user.unfollow(id);

            new Activity(id, ActivityType.friendAccept, null, user.getId()).queue();
        }
    }

    public void ignoreFriendRequest(ObjectId userId) {
        MongoCollection col = jongo.getCollection("friend");
        Date date = new Date();

        col.update("{user_id:#,friend_id:#,status:#}", id, userId, 1)
            .with("{$set:{status:#,modified':#}}", 3, date);

        Activity.remove(userId, ActivityType.friendRequest, id);
    }

    public void cancelFriendRequest(ObjectId userId) {
        MongoCollection col = jongo.getCollection("friend");

        col.remove("{user_id:#,friend_id:#,status:#}", id, userId, 0);
        col.remove("{user_id:#,friend_id:#,status:#}", userId, id, 1);

        del(id);

        Activity.remove(id, ActivityType.friendRequest, userId);
    }

    public void unfriend(ObjectId userId) {
        MongoCollection col = jongo.getCollection("friend");

        col.remove("{user_id:#,friend_id:#,status:#}", id, userId, 2);
        col.remove("{user_id:#,friend_id:#,status:#}", userId, id, 2);

        del(id, userId);
    }

    public void follow(ObjectId userId) {
        if (friends.contains(userId)) {
            MongoCollection col = jongo.getCollection("user");
            col.update(userId)
                .with("{$pull:{unfollowers:#}}", id);

            del(userId);
        } else {
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
    }

    public void unfollow(ObjectId userId) {
        if (friends.contains(userId)) {
            MongoCollection col = jongo.getCollection("user");
            col.update(userId)
                .with("{$addToSet:{unfollowers:#}}", id);

            del(userId);
        } else {
            MongoCollection following = jongo.getCollection("following");
            MongoCollection follower = jongo.getCollection("follower");

            following.remove("{user_id:#,following_id:#}", id, userId);
            follower.remove("{user_id:#,follower_id:#}", userId, id);

            del(id, userId);
        }
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

    public Page getFriend(int skip, int limit) {
        return page(friends, skip, limit, Skim.class);
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
                    user.friends = Friend.getFriendIds(userId);
                    user.followings = Following.getFollowingIds(userId);
                    user.followers = Follower.getFollowerIds(userId);
                    user.friendRequests = Friend.getFriendRequestIds(userId);
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

    public static void online(String userId, String token) {
        @SuppressWarnings("unchecked")
        long online = (Long)evalScript("online", userId, token, Long.toString(now()));

        if (online == 1) {
            User user = get(new ObjectId(userId));

            if (user.invisibility)
                return;

            ObjectNode result = mapper.createObjectNode();
            result.put("action", "online")
                .put("user_id", userId)
                .put("name", user.getName());
            if (user.getAvatar() != null)
                result.put("avatar", user.getAvatar().toString());

            if (user.getFriends() != null)
                publish("user", user.getFriends() + "\n" + result);
        }
    }

    public static void offline(String userId, String token) {
        @SuppressWarnings("unchecked")
        long offline = (Long)evalScript("offline", userId, token);

        if (offline == 1) {
            User user = get(new ObjectId(userId));

            if (user.invisibility)
                return;

            ObjectNode result = mapper.createObjectNode();
            result.put("action", "offline")
                .put("user_id", userId)
                .put("name", user.getName());
            if (user.getAvatar() != null)
                result.put("avatar", user.getAvatar().toString());

            if (user.getFriends() != null)
                publish("user", user.getFriends() + "\n" + result);
        }
    }

    public Page getOnlineFriend(long until, int limit) {
        List<Skim> skims = new ArrayList<Skim>();
        String previous = null;

        Set<ObjectId> ids = getFriends();
        if (ids == null || ids.size() == 0)
            return new Page(skims, previous);

        if (!exists("friends:user_id:" + id)) {
            Map<String, Double>scoreMembers = new HashMap<String, Double>(ids.size());
            Iterator<ObjectId> iterator = ids.iterator();
            while (iterator.hasNext())
                scoreMembers.put(iterator.next().toString(), 0.0);

            zadd("friends:user_id:" + id, scoreMembers);
        }

        zinterstore("friends:online:" + id, "online:user_id", "friends:user_id:" + id);

        Set<Tuple> tuple = zrevrangeByScoreWithScores("friends:online:" + id,
            until - 1, 0, 0, limit);

        if (tuple != null && tuple.size() > 0) {
            for (Tuple t : tuple) {
                Skim skim = Skim.get(new ObjectId(t.getElement()));
                if (skim != null) {
                    until = (long)t.getScore();
                    skim.activity = new Date(until);
                    skims.add(skim);
                }
            }
        }

        if (tuple.size() == limit)
            previous = String.format("until=%d&limit=%d", until, limit);

        return new Page(skims, previous);
    }

    private static void del(ObjectId id) {
        del("user:" + id, "name:" + id, "avatar:" + id, "friends:user_id:" + id);
    }

    private static void del(ObjectId id1, ObjectId id2) {
        del("user:" + id1, "name:" + id1, "avatar:" + id1, "friends:user_id:" + id1,
            "user:" + id2, "name:" + id2, "avatar:" + id2, "friends:user_id:" + id2);
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

    public void hi(ObjectId userId) {
        MongoCollection col = jongo.getCollection("activity");

        Activity activity = col
            .findOne("{type:#,user_id:#,receivers:#,created:{$gt:#}}",
                ActivityType.hi.value(), id, userId, new Date(now() - 86400000L))
            .projection("{_id:1}")
            .as(Activity.class);

        if (activity == null)
            new Activity(id, ActivityType.hi, null, userId).queue();
    }
}
