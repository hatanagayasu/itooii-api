package models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Activity extends Model {
    private static LinkedBlockingQueue<Activity> queue = new LinkedBlockingQueue<Activity>();
    private static Map<String, Set<Integer>> types = new HashMap<String, Set<Integer>>();

    @Id
    ObjectId id;
    @JsonIgnore
    private String action;
    @JsonProperty("user_id")
    @Postproduct
    private ObjectId userId;
    private int type;
    @JsonProperty("post_id")
    private ObjectId postId;
    @JsonProperty("event_id")
    private ObjectId eventId;
    private Set<ObjectId> receivers;
    private Date created;

    static {
        types.put("notifications", new HashSet<Integer>(Arrays.asList(new Integer[] {3, 4, 5, 6, 8, 9, 12, 14, 17})));
        types.put("friends", new HashSet<Integer>(Arrays.asList(new Integer[] {18, 19})));

        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Activity activity = queue.take();
                        activity.update();
                    }
                    catch (Exception e) {
                        errorlog(e);
                    }
                }
            }
        }).start();
    }

    public Activity() {
    }

    private Activity(ObjectId userId, ActivityType type, ObjectId postId) {
        this.id = new ObjectId();
        this.action = types.get("notifications").contains(type.value()) ? "activity/notifications" :
            (types.get("friends").contains(type.value()) ? "activity/friends" : "activity");
        this.userId = userId;
        this.type = type.value();
        this.postId = postId;
        this.created = new Date();
    }

    public Activity(ObjectId userId, ActivityType type, ObjectId postId, ObjectId receiver) {
        this(userId, type, postId, null, receiver);
    }

    public Activity(ObjectId userId, ActivityType type, ObjectId postId, ObjectId eventId,
        ObjectId receiver) {
        this(userId, type, postId);

        this.eventId = eventId;
        this.receivers = new HashSet<ObjectId>(1);
        this.receivers.add(receiver);
    }

    public Activity(ObjectId userId, ActivityType type, ObjectId postId, Set<ObjectId> receivers) {
        this(userId, type, postId, null, receivers);
    }

    public Activity(ObjectId userId, ActivityType type, ObjectId postId, ObjectId eventId,
        Set<ObjectId> receivers) {
        this(userId, type, postId);

        this.eventId = eventId;
        this.receivers = receivers;
    }

    public void queue() {
        try {
            queue.put(this);
        } catch (InterruptedException e) {
            errorlog(e);
        }
    }

    private void update() {
        MongoCollection actCol = jongo.getCollection("activity");
        MongoCollection feedCol = jongo.getCollection("feed");

        if (receivers == null || receivers.size() == 0)
            return;

        actCol.save(this);

        if (postId != null && type != ActivityType.followYou.value()) {
            Date modified = new Date();
            for (ObjectId receiver : receivers) {
                feedCol.update("{user_id:#,post_id:#}", receiver, postId)
                    .upsert()
                    .with("{$push:{'relevants':{user_id:#,type:#,created:#}},$set:{modified:#}}",
                            userId, type, created, modified);
            }
        }

        Set<ObjectId> receivers = this.receivers;
        this.receivers = null;

        publish("user", receivers + "\n" + this);
    }

    public static long getUnreadNotificationCount(User user, String type) {
        MongoCollection col = jongo.getCollection("activity");
        ObjectId lastReadNotification = user.getLastReadNotification() != null ?
            user.getLastReadNotification() : new ObjectId(new Date(0));

        return col.count("{receivers:#,type:{$in:#},_id:{$gt:#}}",
            user.getId(), types.get(type), lastReadNotification);
    }

    public static Page getNotifications(User user, Date until, int limit, String type) {
        MongoCollection col = jongo.getCollection("activity");
        String previous = null;

        MongoCursor<Activity> cursor = col
            .find("{receivers:#,type:{$in:#},created:{$lt:#}}",
                user.getId(), types.get(type), until)
            .sort("{created:-1}")
            .limit(limit)
            .projection("{receivers:0}")
            .as(Activity.class);

        List<Activity> activities = new ArrayList<Activity>(limit);
        Activity activity = null;
        while (cursor.hasNext()) {
            activity = cursor.next();
            activities.add(activity);

            if (activity.type == ActivityType.friendAccept.value())
                activity.remove();
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (activities.size() > 0) {
            ObjectId id = activities.get(0).getId();
            if (user.getLastReadNotification() == null ||
                id.compareTo(user.getLastReadNotification()) > 0)
                user.updateLastReadNotificaotion(id);

            ObjectNode event = mapper.createObjectNode();
            event.put("action", "badge/update")
                .put(type, 0);

            publish("user", user.getId() + "\n" + event);
        }

        if (activities.size() == limit)
            previous = String.format("until=%d&limit=%d", activity.created.getTime(), limit);

        return new Page(activities, previous);
    }

    public void remove() {
        MongoCollection col = jongo.getCollection("activity");

        col.remove(id);
    }

    public static void remove(ObjectId userId, ActivityType type, ObjectId receiver) {
        MongoCollection col = jongo.getCollection("activity");

        col.remove("{user_id:#,receivers:[#],type:#}", userId, receiver, type.value());
    }
}
