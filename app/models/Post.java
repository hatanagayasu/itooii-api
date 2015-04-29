package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Post extends Model {
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    @JsonIgnore
    @JsonProperty("user_name")
    private String userName;
    private String text;
    private List<Attachment> attachments;
    private Date created;
    @JsonProperty("comment_count")
    private int commentCount;
    private List<Comment> comments;
    @JsonProperty("like_count")
    private int likeCount;
    private Set<ObjectId> likes;

    public Post() {
    }

    public Post(ObjectId userId, String text, List<Attachment> attachments) {
        this.id = new ObjectId();
        this.userId = userId;
        this.text = text;
        this.attachments = attachments == null ? null :
            (attachments.isEmpty() ? null : attachments);
        this.created = new Date();
        this.commentCount = 0;
        this.likeCount = 0;
    }

    public void save(User user) {
        MongoCollection postCol = jongo.getCollection("post");

        postCol.save(this);

        Feed.update(user, id);
    }

    public void postproduct(ObjectId userId) {
        userName = name(this.userId);

        if (comments != null)
            Comment.postproduct(comments, userId);
    }

    public static Post get(ObjectId postId) {
        String key = "post:" + postId;

        Post post = cache(key, Post.class, new Callable<Post>() {
            public Post call() {
                MongoCollection postCol = jongo.getCollection("post");

                Post post = postCol.findOne(postId).as(Post.class);

                return post;
            }
        });

        return post;
    }

    public static Page getTimeline(ObjectId userId, long until, int limit) {
        MongoCollection postCol = jongo.getCollection("post");
        String previous = null;

        MongoCursor<Post> cursor = postCol
            .find("{user_id:#,created:{$lt:#}}", userId, new Date(until))
            .sort("{created:-1}")
            .limit(limit)
            .as(Post.class);

        List<Post> posts = new ArrayList<Post>(limit);
        while (cursor.hasNext()) {
            Post post = cursor.next();
            post.postproduct(userId);
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

    public static void like(ObjectId postId, ObjectId userId) {
        MongoCollection postCol = jongo.getCollection("post");

        Post post = postCol.findAndModify("{_id:#,likes:{$ne:#}}", postId, userId)
            .with("{$addToSet:{likes:#},$inc:{like_count:1}}", userId)
            .projection("{_id:1}")
            .as(Post.class);

        if (post != null)
            del("post:" + postId);
    }

    public static void unlike(ObjectId postId, ObjectId userId) {
        MongoCollection postCol = jongo.getCollection("post");

        Post post = postCol.findAndModify("{_id:#,likes:#}", postId, userId)
            .with("{$pull:{likes:#},$inc:{like_count:-1}}", userId)
            .projection("{_id:1}")
            .as(Post.class);

        if (post != null)
            del("post:" + postId);
    }
}
