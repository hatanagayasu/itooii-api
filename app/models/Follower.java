package models;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Follower extends Model {
    private static final long serialVersionUID = -1;

    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    @JsonProperty("follower_id")
    private ObjectId followerId;
    private Date created;

    public Follower() {
    }

    public Follower(ObjectId userId, ObjectId followerId) {
        id = new ObjectId();
        this.userId = userId;
        this.followerId = followerId;
        created = new Date();
    }

    public static Set<ObjectId> getFollowerIds(ObjectId userId) {
        MongoCollection followerCol = jongo.getCollection("follower");
        MongoCursor<Follower> cursor = followerCol.find("{user_id:#}", userId)
            .projection("{follower_id:1}")
            .as(Follower.class);

        Set<ObjectId> followers = new HashSet<ObjectId>();
        while (cursor.hasNext())
            followers.add(cursor.next().getFollowerId());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return followers;
    }
}
