package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Activity extends Model {
    private static LinkedBlockingQueue<Activity> queue = new LinkedBlockingQueue<Activity>();

    @Id
    ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    @JsonIgnore
    private String name;
    @JsonIgnore
    private ObjectId avatar;
    private int type;
    @JsonProperty("post_id")
    private ObjectId postId;
    private Set<ObjectId> receivers;
    private Date created;

    static {
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
        this.userId = userId;
        this.type = type.value();
        this.postId = postId;
        this.created = new Date();
    }

    public Activity(ObjectId userId, ActivityType type, ObjectId postId, ObjectId receiver) {
        this(userId, type, postId);

        this.receivers = new HashSet<ObjectId>(1);
        this.receivers.add(receiver);
    }

    public Activity(ObjectId userId, ActivityType type, ObjectId postId, Set<ObjectId> receivers) {
        this(userId, type, postId);

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

        Date modified = new Date();
        for (ObjectId receiver : receivers) {
            feedCol.update("{user_id:#,post_id:#}", receiver, postId)
                .upsert()
                .with("{$push:{'relevants':{user_id:#,type:#,created:#}},$set:{modified:#}}",
                        userId, type, created, modified);
        }
    }

    public void postproduct() {
        name = name(userId);
        avatar = avatar(userId);
    }

    public static Page getNotifications(ObjectId userId, Date until, int limit) {
        MongoCollection col = jongo.getCollection("activity");
        String previous = null;

        MongoCursor<Activity> cursor = col
            .find("{receivers:#,type:{$in:[3,4,5,6,8,9]},created:{$lt:#}}", userId, until)
            .sort("{created:-1}")
            .limit(limit)
            .projection("{receivers:0}")
            .as(Activity.class);

        List<Activity> activities = new ArrayList<Activity>(limit);
        Activity activity = null;
        while (cursor.hasNext()) {
            activity = cursor.next();
            activity.postproduct();
            activities.add(activity);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (activities.size() == limit)
            previous = String.format("until=%d&limit=%d", activity.created.getTime(), limit);

        return new Page(activities, previous);
    }
}
