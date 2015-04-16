package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Following extends Model {
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    @JsonProperty("following_id")
    private ObjectId followingId;
    private Date created;

    public Following() {
    }

    public Following(ObjectId userId, ObjectId followingId) {
        id = new ObjectId();
        this.userId = userId;
        this.followingId = followingId;
        created = new Date();
    }

    public static Set<ObjectId> getFollowingIds(ObjectId userId) {
        MongoCollection followingCol = jongo.getCollection("following");
        MongoCursor<Following> cursor = followingCol.find("{user_id:#}", userId)
            .projection("{following_id:1}")
            .as(Following.class);

        Set<ObjectId> followings = new HashSet<ObjectId>();
        while (cursor.hasNext())
            followings.add(cursor.next().getFollowingId());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return followings;
    }

    public static Page get(User user, int skip, int limit) {
        List<Other> others = new ArrayList<Other>();
        String next = null;

        if (user.getFollowings() != null) {
            Iterator<ObjectId> iterator = user.getFollowings().iterator();

            int count = 0;
            while (count < skip && iterator.hasNext()) {
                iterator.next();
                count++;
            }

            count = 0;
            while (count < limit && iterator.hasNext()) {
                Other other = Other.get(iterator.next());
                if (other != null)
                   others.add(other);
                count++;
            }

            if (iterator.hasNext())
                next = String.format("skip=%d&limit=%d", skip + limit, limit);
        }

        return new Page(others, null, next);
    }
}
