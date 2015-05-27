package models;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Activity extends Model {
    private static LinkedBlockingQueue<Activity> queue = new LinkedBlockingQueue<Activity>();

    @Id
    ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
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

        actCol.update("{user_id:#,type:#,post_id:#}", userId, type, postId)
            .upsert()
            .with("{$addToSet:{receivers:#},$setOnInsert:{created:#}}", receivers, created);

        Date modified = new Date();
        for (ObjectId receiver : receivers) {
            feedCol.update("{user_id:#,post_id:#}", receiver, postId)
                .upsert()
                .with("{$push:{'relevants':{user_id:#,type:#,created:#}},$set:{modified:#}}",
                        userId, type, created, modified);
        }
    }
}
