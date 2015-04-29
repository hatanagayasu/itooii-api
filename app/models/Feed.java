package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Feed extends Model {
    @Id
    private ObjectId id;
    private Date modified;
    @JsonProperty("post_ids")
    private List<ObjectId> postIds;

    public Feed() {
    }

    private Feed(User user) {
        MongoCollection postCol = jongo.getCollection("post");

        id = user.getId();
        modified = new Date();
        postIds = new ArrayList<ObjectId>(100);

        Set<ObjectId> ids = new HashSet<ObjectId>();
        Set<ObjectId> followings = user.getFollowings();
        if (followings != null)
            ids.addAll(followings);
        ids.add(user.getId());

        MongoCursor<Post> cursor = postCol.find("{user_id:{$in:#}}", ids)
            .sort("{created:-1}")
            .limit(100)
            .projection("{_id:1}")
            .as(Post.class);

        while (cursor.hasNext())
            postIds.add(cursor.next().getId());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void update(User user, ObjectId postId) {
        MongoCollection feedCol = jongo.getCollection("feed");

        Set<ObjectId> ids = new HashSet<ObjectId>();
        Set<ObjectId> followers = user.getFollowers();
        if (followers != null)
            ids.addAll(followers);
        ids.add(user.getId());

        feedCol.update("{_id:{$in:#}}", ids)
            .with("{$push:{post_ids:{$each:[#],$position:0,$slice:100}}}", postId);
    }

    public static Page get(User user, long until, int limit) {
        MongoCollection postCol = jongo.getCollection("post");
        String previous = null;

        Set<ObjectId> ids = new HashSet<ObjectId>();
        ids.addAll(user.getFollowings());
        ids.add(user.getId());

        MongoCursor<Post> cursor = postCol
            .find("{user_id:{$in:#},created:{$lt:#}}", ids, new Date(until))
            .sort("{created:-1}")
            .limit(limit)
            .as(Post.class);

        List<Post> posts = new ArrayList<Post>(limit);
        while (cursor.hasNext()) {
            Post post = cursor.next();
            post.postproduct(user.getId());
            posts.add(post);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (posts.size() == limit) {
            until = posts.get(posts.size() - 1).getCreated().getTime();
            previous = String.format("until=%d&limit=%d", until, limit);
        }

        return new Page(posts, previous);
    }

    public static Page get(User user, int skip, long until, int limit) {
        MongoCollection feedCol = jongo.getCollection("feed");
        String previous = null;

        Feed feed = feedCol.findOne(user.getId())
            .projection("{post_ids:{$slice:[#,#]}}", skip, limit)
            .as(Feed.class);

        if (feed == null) {
            feed = new Feed(user);
            feedCol.save(feed);

            if (skip > 0)
                feed.postIds.subList(0, Math.min(skip, feed.postIds.size())).clear();

            if (limit < feed.postIds.size())
                feed.postIds.subList(limit, feed.postIds.size()).clear();
        }

        List<Post> posts = new ArrayList<Post>(feed.postIds.size());
        for (ObjectId postId : feed.postIds) {
            Post post = Post.get(postId);
            if (post != null && post.getCreated().getTime() < until) {
                post.postproduct(user.getId());
                posts.add(post);
            }
        }

        if (posts.size() > 0) {
            until = posts.get(posts.size() - 1).getCreated().getTime();

            if (feed.postIds.size() < limit || skip + limit >= 100)
                previous = String.format("until=%d&limit=%d", until, limit);
            else
                previous = String.format("skip=%d&until=%d&limit=%d", skip + limit, until, limit);
        }

        return new Page(posts, previous);
    }
}
