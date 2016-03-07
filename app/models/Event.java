package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

import redis.clients.jedis.Tuple;

@lombok.Getter
public class Event extends Model {
    @Id
    private ObjectId id;
    private String alias;
    @JsonProperty("user_id")
    private ObjectId userId;
    private String name;
    private String details;
    private int lang0;
    private int lang1;
    private Date from;
    private Date to;
    private Date created;
    private Boolean deleted;
    private Set<ObjectId> members;

    public Event() {
    }

    public Event(ObjectId userId, String name, String details, int lang0, int lang1, Date from,
        Date to) {
        this.id = new ObjectId();
        this.userId = userId;
        this.name = name;
        this.details = details;
        this.lang0 = lang0;
        this.lang1 = lang1;
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

    public static Event getByAlias(String alias) {
        String key = "event:" + alias;

        return cache(key, Event.class, new Callable<Event>() {
            public Event call() {
                MongoCollection col = jongo.getCollection("event");
                Event event = col.findOne("{alias:#}", alias).as(Event.class);

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

    public Page getOnlineUser(User me, ObjectId eventId, long until, int limit) {
        String previous = null;

        Set<Tuple> tuple = zrevrangeByScoreWithScores("event:online_user_id:" + eventId,
            until - 1, 0, 0, limit);

        List<Skim> skims = new ArrayList<Skim>();

        if (tuple != null && tuple.size() > 0) {
            for (Tuple t : tuple) {
                Skim skim = Skim.get(new ObjectId(t.getElement()));
                if (skim != null) {
                    if (me != null) {
                        User user = User.get(skim.getId());
                        if (user.getBlockings() != null && user.getBlockings().contains(me.getId()))
                            continue;
                    }

                    until = (long)t.getScore();
                    skim.activity = new Date(until);
                    skim.setTalking(sismember("event:talking:" + eventId, skim.getId().toString()));
                    skims.add(skim);
                }
            }
        }

        if (tuple.size() == limit)
            previous = String.format("until=%d&limit=%d", until, limit);

        return new Page(skims, previous);
    }

    public boolean isOnline(ObjectId userId) {
        return zrank("event:online_user_id:" + id, userId.toString()) != null;
    }

    public void enter(User me, String token) {
        String eventId = id.toString();
        String userId = me == null ? "" : me.getId().toString();

        @SuppressWarnings("unchecked")
        List<String> sessions = (List<String>)evalScript("enter_event",
            eventId, userId, token, Long.toString(now()));
        sessions.remove(token);
        if (me != null && sessions != null && sessions.size() > 0) {
            Skim skim = Skim.get(new ObjectId(userId));
            skim.setTalking(sismember("event:talking:" + eventId, userId));

            ObjectNode result = mapper.createObjectNode();
            result.put("action", "event/enter")
                .put("event_id", eventId)
                .putPOJO("data", skim);

            Iterator<String> iterator = sessions.iterator();
            while (iterator.hasNext()) {
                User user = User.getByToken(iterator.next());
                if (user.getBlockings() != null && user.getBlockings().contains(me.getId()))
                    iterator.remove();
            }

            publish("session", sessions + "\n" + result);
        }
    }

    public static void exit(String userId, String token) {
        String eventId = hget("token:event", token);

        if (eventId != null) {
            Event event = Event.get(new ObjectId(eventId));
            event.exit(User.get(new ObjectId(userId)), token);
        }
    }

    public void exit(User me, String token) {
        String eventId = id.toString();
        String userId = me == null ? "" : me.getId().toString();

        @SuppressWarnings("unchecked")
        List<String> sessions = (List<String>)evalScript("exit_event", eventId, userId, token);
        if (me != null && sessions != null && sessions.size() > 0) {
            ObjectNode result = mapper.createObjectNode();
            result.put("action", "event/exit")
                .put("event_id", eventId)
                .put("user_id", userId);

            Iterator<String> iterator = sessions.iterator();
            while (iterator.hasNext()) {
                User user = User.getByToken(iterator.next());
                if (user.getBlockings() != null && user.getBlockings().contains(me.getId()))
                    iterator.remove();
            }

            publish("session", sessions + "\n" + result);
        }
    }

    public static Page gets(ObjectId userId, Date until, int limit) {
        MongoCollection col = jongo.getCollection("event");
        String previous = null;

        MongoCursor<Event> cursor = col
            .find("{members:#,from:{$lt:#},deleted:{$exists:false}}", userId, until)
            .sort("{from:-1}")
            .limit(limit)
            .projection("{members:-1}")
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
            .projection("{members:-1}")
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
