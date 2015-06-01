package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Feed extends Model {
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    @JsonProperty("post_id")
    private ObjectId postId;
    private Date modified;
    private List<Relevant> relevants;

    public Feed() {
    }

    public static Page get(ObjectId userId, Date until, int limit) {
        MongoCollection col = jongo.getCollection("feed");
        String previous = null;

        MongoCursor<Feed> cursor = col
            .find("{user_id:#,modified:{$lt:#}}", userId, until)
            .sort("{modified:-1}")
            .limit(limit)
            .projection("{post_id:1,modified:1,relevants:1}")
            .as(Feed.class);

        List<Post> posts = new ArrayList<Post>(limit);
        Feed feed = null;
        while (cursor.hasNext()) {
            feed = cursor.next();
            Post post = Post.get(feed.postId);
            post.postproduct(userId, feed.relevants);
            posts.add(post);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (posts.size() == limit)
            previous = String.format("until=%d&limit=%d", feed.modified.getTime(), limit);

        return new Page(posts, previous);
    }
}
