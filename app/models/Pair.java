package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Pair extends Model {
    @Id
    private ObjectId id;
    @JsonIgnore
    private String action;
    private ObjectId offerId;
    @JsonIgnore
    @JsonProperty("offer_name")
    private String offerName;
    @JsonIgnore
    @JsonProperty("offer_avatar")
    private ObjectId offerAvatar;
    private ObjectId answerId;
    @JsonIgnore
    @JsonProperty("answer_name")
    private String answerName;
    @JsonIgnore
    @JsonProperty("answer_avatar")
    private ObjectId answerAvatar;
    private int lang0, lang1;
    @JsonProperty("event_id")
    private ObjectId eventId;
    private Date created;

    public Pair() {
    }

    public Pair(ObjectId offerId, ObjectId answerId, int lang0, int lang1, ObjectId eventId) {
        this.id = new ObjectId();
        this.action = "pair";
        this.offerId = offerId;
        this.answerId = answerId;
        this.lang0 = lang0;
        this.lang1 = lang1;
        this.eventId = eventId;
        this.created = new Date();

        postproduct();
    }

    public static Page get(ObjectId eventId, Date until, int limit) {
        MongoCollection col = jongo.getCollection("pair");
        String previous = null;

        Date boundary = new Date(now() - 10 * 60 * 1000);
        Find find = eventId == null ?
            col.find("{created:{$lt:#,$gt:#}}", until, boundary) :
            col.find("{event_id:#,created:{$lt:#,$gt:#}}", eventId, until, boundary);
        MongoCursor<Pair> cursor = find
            .sort("{created:-1}")
            .limit(limit)
            .as(Pair.class);

        List<Pair> pairs = new ArrayList<Pair>(limit);
        Pair pair = null;
        while (cursor.hasNext()) {
            pair = cursor.next();
            pairs.add(pair);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (pairs.size() == limit)
            previous = String.format("until=%d&limit=%d", pair.created.getTime(), limit);

        return new Page(pairs, previous);
    }

    public void postproduct() {
        offerName = name(offerId);
        offerAvatar = avatar(offerId);
        answerName = name(answerId);
        answerAvatar = avatar(answerId);
    }

    public void save() {
        MongoCollection col = jongo.getCollection("pair");

        col.save(this);
    }
}
