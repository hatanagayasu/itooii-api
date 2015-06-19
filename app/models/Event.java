package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Event extends Model {
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    private String name;
    private String details;
    private int language;
    private Date from;
    private Date to;
    private Date created;
    private Boolean deleted;
    private Set<ObjectId> members;

    public Event() {
    }

    public Event(ObjectId userId, String name, String details, int language, Date from, Date to) {
        this.id = new ObjectId();
        this.userId = userId;
        this.name = name;
        this.details = details;
        this.language = language;
        this.from = from;
        this.to = to;
        this.created = new Date();
        this.members = new HashSet<ObjectId>(1);

        this.members.add(userId);
    }

    public void save(User user) {
        MongoCollection col = jongo.getCollection("event");

        col.save(this);

        List<Attachment> attachments = new ArrayList<Attachment>(1);
        attachments.add(new Attachment(AttachmentType.create_event, id, name));
        Post post = new Post(user.getId(), null, attachments, true);
        post.save();

        if (user.getFollowers() != null) {
            new Activity(user.getId(), ActivityType.createEvent, post.getId(), user.getFollowers())
                .queue();
        }
    }

    public void update(JsonNode params) {
        MongoCollection col = jongo.getCollection("event");

        if (params.size() > 0)
            col.update(id).with("{$set:#}", params);

        del("event:" + id);
    }

    public static Event get(ObjectId eventId) {
        String key = "event:" + eventId;

        return cache(key, Event.class, new Callable<Event>() {
            public Event call() {
                MongoCollection col = jongo.getCollection("event");
                Event event = col.findOne(eventId).as(Event.class);

                return event;
            }
        });
    }

    public void join(User user) {
        MongoCollection col = jongo.getCollection("event");

        col.update(id).with("{$addToSet:{members:#}}", userId);

        del("event:" + id);

        List<Attachment> attachments = new ArrayList<Attachment>(1);
        attachments.add(new Attachment(AttachmentType.join_event, id, name));
        Post post = new Post(userId, null, attachments, true);
        post.save();

        if (user.getFollowers() != null) {
            new Activity(user.getId(), ActivityType.joinEvent, post.getId(), user.getFollowers())
                .queue();
        }
    }

    public void leave(ObjectId userId) {
        MongoCollection col = jongo.getCollection("event");

        col.update(id).with("{$pull:{members:#}}", userId);

        del("event:" + id);
    }

    public void delete() {
        MongoCollection col = jongo.getCollection("event");

        col.update(id).with("{$set:{deleted:true}}");

        del("event:" + id);
    }

    public static Page gets(ObjectId userId, Date until, int limit) {
        MongoCollection col = jongo.getCollection("event");
        String previous = null;

        MongoCursor<Event> cursor = col
            .find("{members:#,from:{$lt:#},deleted:{$exists:false}}", userId, until)
            .sort("{from:-1}")
            .limit(limit)
            .as(Event.class);

        List<Event> events = new ArrayList<Event>(limit);
        Event event = null;
        while (cursor.hasNext()) {
            event = cursor.next();
            events.add(event);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (events.size() == limit)
            previous = String.format("until=%d&limit=%d", event.from.getTime(), limit);

        return new Page(events, previous);
    }

    public static Page getHosting(ObjectId userId, Date until, int limit) {
        MongoCollection col = jongo.getCollection("event");
        String previous = null;

        MongoCursor<Event> cursor = col
            .find("{user_id:#,from:{$lt:#},deleted:{$exists:false}}", userId, until)
            .sort("{from:-1}")
            .limit(limit)
            .as(Event.class);

        List<Event> events = new ArrayList<Event>(limit);
        Event event = null;
        while (cursor.hasNext()) {
            event = cursor.next();
            events.add(event);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (events.size() == limit)
            previous = String.format("until=%d&limit=%d", event.from.getTime(), limit);

        return new Page(events, previous);
    }

    public static Page search(Date until, int limit) {
        MongoCollection col = jongo.getCollection("event");
        String previous = null;

        MongoCursor<Event> cursor = col
            .find("{from:{$lt:#},deleted:{$exists:false}}", until)
            .sort("{from:-1}")
            .limit(limit)
            .as(Event.class);

        List<Event> events = new ArrayList<Event>(limit);
        Event event = null;
        while (cursor.hasNext()) {
            event = cursor.next();
            events.add(event);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (events.size() == limit)
            previous = String.format("until=%d&limit=%d", event.from.getTime(), limit);

        return new Page(events, previous);
    }
}
