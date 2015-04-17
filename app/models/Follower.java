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
public class Follower extends Model {
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

    public static Page get(User user, int skip, int limit) {
        List<Skim> skims = new ArrayList<Skim>();
        String next = null;

        if (user.getFollowers() != null) {
            Iterator<ObjectId> iterator = user.getFollowers().iterator();

            int count = 0;
            while (count < skip && iterator.hasNext()) {
                iterator.next();
                count++;
            }

            count = 0;
            while (count < limit && iterator.hasNext()) {
                Skim skim = Skim.get(iterator.next());
                if (skim != null)
                   skims.add(skim);
                count++;
            }

            if (iterator.hasNext())
                next = String.format("skip=%d&limit=%d", skip + limit, limit);
        }

        return new Page(skims, null, next);
    }
}
