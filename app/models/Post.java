package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
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
    @JsonIgnore
    @JsonProperty("user_avatar")
    private ObjectId userAvatar;
    private String text;
    private List<Attachment> attachments;
    private Date created;
    @JsonProperty("comment_count")
    private int commentCount;
    private Set<ObjectId> commentators;
    private List<Comment> comments;
    @JsonProperty("like_count")
    private int likeCount;
    private Set<ObjectId> likes;
    private Boolean automatic;
    @JsonIgnore
    private List<Relevant> relevants;

    public Post() {
    }

    public Post(ObjectId userId, String text, List<Attachment> attachments) {
        this(userId, text, attachments, null);
    }

    public Post(ObjectId userId, String text, List<Attachment> attachments, Boolean automatic) {
        this.id = new ObjectId();
        this.userId = userId;
        this.text = text;
        this.attachments = attachments == null ? null :
            (attachments.isEmpty() ? null : attachments);
        this.created = new Date();
        this.commentCount = 0;
        this.likeCount = 0;
        this.automatic = automatic;

        this.userName = name(userId);
        this.userAvatar = avatar(userId);
    }

    public void save() {
        save(null);
    }

    public void save(User user) {
        if (attachments != null) {
            List<ObjectId>ids = new ArrayList<ObjectId>();
            for (Attachment attachment : attachments) {
                if (attachment.getType() != AttachmentType.url)
                    ids.add(attachment.getId());
            }

            if (ids.size() > 0)
                Media.posted(ids);
        }

        MongoCollection postCol = jongo.getCollection("post");

        postCol.save(this);

        if (user != null) {
            Set<ObjectId> ids = new HashSet<ObjectId>();
            ids.add(user.getId());
            if (user.getFollowers() != null)
                ids.addAll(user.getFollowers());
            new Activity(userId, ActivityType.post, id, ids).queue();
        }
    }

    public void postproduct(ObjectId userId) {
        userName = name(this.userId);
        userAvatar = avatar(this.userId);

        if (comments != null)
            Comment.postproduct(comments, userId);
    }

    public void postproduct(ObjectId userId, List<Relevant> relevants) {
        postproduct(id);

        if (relevants != null) {
            this.relevants = relevants;
            for (Relevant relevant : relevants)
                relevant.postproduct();
        }
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
            .find("{user_id:#,created:{$lt:#},automatic:{$ne:true}}", userId, new Date(until))
            .sort("{created:-1}")
            .limit(limit)
            .as(Post.class);

        List<Post> posts = new ArrayList<Post>(limit);
        Post post = null;
        while (cursor.hasNext()) {
            post = cursor.next();
            post.postproduct(userId);
            posts.add(post);
        }

        if (cursor.count() == limit) {
            until = post.getCreated().getTime();
            previous = String.format("until=%d&limit=%d", until, limit);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new Page(posts, previous);
    }

    public static void like(ObjectId postId, ObjectId userId) {
        MongoCollection postCol = jongo.getCollection("post");

        Post post = postCol.findAndModify("{_id:#,likes:{$ne:#}}", postId, userId)
            .with("{$addToSet:{likes:#},$inc:{like_count:1}}", userId)
            .projection("{_id:1,user_id:1,commentators:1,likes:1}")
            .as(Post.class);

        if (post != null) {
            del("post:" + postId);

            if (!userId.equals(post.getUserId())) {
                new Activity(userId, ActivityType.likeYourPost, postId, post.getUserId()).queue();
            }
            Set<ObjectId> commentators = post.getCommentators();
            if (commentators != null) {
                commentators.remove(userId);
                commentators.remove(post.getUserId());
                new Activity(userId, ActivityType.likePostYouComment, postId, commentators).queue();
            }
        }
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
