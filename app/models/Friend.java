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
public class Friend extends Model {
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    @JsonProperty("friend_id")
    private ObjectId friendId;
    private Date created;

    public Friend() {
    }

    public Friend(ObjectId userId, ObjectId friendId) {
        id = new ObjectId();
        this.userId = userId;
        this.friendId = friendId;
        created = new Date();
    }

    public static Set<ObjectId> getFriendIds(ObjectId userId) {
        return getIds(userId, 2);
    }

    public static Set<ObjectId> getFriendRequestIds(ObjectId userId) {
        return getIds(userId, 0);
    }

    private static Set<ObjectId> getIds(ObjectId userId, int status) {
        MongoCollection friendCol = jongo.getCollection("friend");
        MongoCursor<Friend> cursor = friendCol.find("{user_id:#,status:#}", userId, status)
            .projection("{friend_id:1}")
            .as(Friend.class);

        Set<ObjectId> friends = new HashSet<ObjectId>();
        while (cursor.hasNext())
            friends.add(cursor.next().getFriendId());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return friends;
    }
}
