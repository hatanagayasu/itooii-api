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
    @JsonProperty("event_id")
    private ObjectId eventId;
    @JsonProperty("user_id")
    @Postproduct
    private ObjectId userId;
    private String text;
    private List<Attachment> attachments;
    private Date created;
    @JsonProperty("comment_count")
    private int commentCount;
    private Set<ObjectId> commentators;
    private List<Comment> comments;
    @JsonIgnore
    @JsonProperty("like_count")
    private int likeCount;
    private Set<ObjectId> likes;
    @JsonIgnore
    private boolean liked;
    private Boolean automatic;
    @JsonIgnore
    private List<Relevant> relevants;
    private Boolean deleted;

    public Post() {
    }

    public Post(ObjectId userId, String text, List<Attachment> attachments) {
        this(null, userId, text, attachments, null);
    }

    public Post(ObjectId userId, String text, List<Attachment> attachments, Boolean automatic) {
        this(null, userId, text, attachments, automatic);
    }

    public Post(ObjectId eventId, ObjectId userId, String text, List<Attachment> attachments) {
        this(eventId, userId, text, attachments, null);
    }

    public Post(ObjectId eventId, ObjectId userId, String text, List<Attachment> attachments,
        Boolean automatic) {
        this.id = new ObjectId();
        this.eventId = eventId;
        this.userId = userId;
        this.text = text;
        this.attachments = attachments == null ? null :
            (attachments.isEmpty() ? null : attachments);
        this.created = new Date();
        this.commentCount = 0;
        this.automatic = automatic;
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
        if (likes == null) {
            likeCount = 0;
            liked = false;
        } else {
            likeCount = likes.size();
            liked = likes.contains(userId);
            likes = null;
        }

        if (comments != null)
            Comment.postproduct(comments, userId);

        if (attachments != null)
            Attachment.postproduct(attachments);
    }

    public void postproduct(ObjectId userId, List<Relevant> relevants) {
        postproduct(userId);
        this.relevants = relevants;
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

    public static Page getTimeline(ObjectId userId, ObjectId myId, Date until, int limit) {
        MongoCollection postCol = jongo.getCollection("post");
        String previous = null;

        MongoCursor<Post> cursor = postCol
            .find("{user_id:#,created:{$lt:#}}", userId, until)
            .sort("{created:-1}")
            .limit(limit)
            .as(Post.class);

        List<Post> posts = new ArrayList<Post>(limit);
        Post post = null;
        int count = 0;
        while (cursor.hasNext()) {
            post = cursor.next();
            count++;
            if (post.deleted == null && post.automatic == null) {
                post.postproduct(myId);
                posts.add(post);
            }
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (count == limit)
            previous = String.format("until=%d&limit=%d", post.getCreated().getTime(), limit);

        return new Page(posts, previous);
    }

    public static Page getEventTimeline(ObjectId eventId, ObjectId myId, Date until, int limit) {
        MongoCollection postCol = jongo.getCollection("post");
        String previous = null;

        MongoCursor<Post> cursor = postCol
            .find("{event_id:#,created:{$lt:#}}", eventId, until)
            .sort("{created:-1}")
            .limit(limit)
            .projection("{event_id:0}")
            .as(Post.class);

        List<Post> posts = new ArrayList<Post>(limit);
        Post post = null;
        int count = 0;
        while (cursor.hasNext()) {
            post = cursor.next();
            count++;
            if (post.deleted == null && post.automatic == null) {
                post.postproduct(myId);
                posts.add(post);
            }
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (count == limit)
            previous = String.format("until=%d&limit=%d", post.getCreated().getTime(), limit);

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

    public static void delete(ObjectId postId, ObjectId userId) {
        MongoCollection postCol = jongo.getCollection("post");

        Post post = postCol.findAndModify("{_id:#,user_id:#}", postId, userId)
            .with("{$set:{deleted:#}}", true)
            .projection("{_id:1}")
            .as(Post.class);

        if (post != null)
            del("post:" + postId);
    }
}
