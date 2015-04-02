package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Comment extends Model {
    private static final long serialVersionUID = -1;

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
    @JsonIgnore
    @JsonProperty("like_count")
    private int likeCount;
    private Set<ObjectId> likes;
    @JsonIgnore
    private boolean liked;

    public Comment() {
    }

    public Comment(ObjectId userId, String text, List<Attachment> attachments) {
        this.id = new ObjectId();
        this.userId = userId;
        this.text = text;
        this.attachments = attachments == null ? null :
            (attachments.isEmpty() ? null : attachments);
        this.created = new Date();
        this.userName = name(userId);
    }

    public void save(ObjectId postId) {
        MongoCollection postCol = jongo.getCollection("post");
        MongoCollection commentCol = jongo.getCollection("comment");

        Post post = postCol
            .findAndModify("{_id:#}", postId)
            .with("{$inc:{comment_count:1},$push:{comments:{$each:[#],$slice:-4}}}", this)
            .projection("{comment_count:1}")
            .as(Post.class);

        if (post == null)
            return;

        int page = post.getCommentCount() / 50;
        commentCol.update("{post_id:#,page:#}", postId, page).upsert()
            .with("{$push:{comments:#},$setOnInsert:{created:#}}", this, this.created);

        expire("post:" + postId);
    }

    public static Page get(ObjectId postId, ObjectId userId, long until, int limit) {
        MongoCollection commentCol = jongo.getCollection("comment");
        String previous = null;

        MongoCursor<Comments> cursor = commentCol
            .find("{post_id:#,created:{$lt:#}}", postId, new Date(until))
            .sort("{created:-1}")
            .limit(2)
            .as(Comments.class);

        List<Comments> commentses = new ArrayList<Comments>(2);
        while (cursor.hasNext())
            commentses.add(cursor.next());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<Comment> comments = new ArrayList<Comment>(100);
        for (int i = commentses.size() - 1; i >= 0; i--) {
            for (Comment comment : commentses.get(i).getComments()) {
                if (comment.created.getTime() < until)
                    comments.add(comment);
            }
        }

        if (comments.size() > limit)
            comments.subList(0, comments.size() - limit).clear();

        postproduction(comments, userId);

        if (comments.size() == limit) {
            until = comments.get(0).getCreated().getTime();
            previous = String.format("until=%d&limit=%d", until, limit);
        }

        return new Page(comments, previous);
    }

    public static void postproduction(List<Comment> comments, ObjectId userId) {
        for (Comment comment : comments) {
            comment.userName = name(comment.userId);

            if (comment.likes == null) {
                comment.likeCount = 0;
                comment.liked = false;
            } else {
                comment.likeCount = comment.likes.size();
                comment.liked = comment.likes.contains(userId);
                comment.likes = null;
            }
        }
    }

    public static void like(ObjectId commentId, ObjectId userId) {
        MongoCollection commentCol = jongo.getCollection("comment");
        MongoCollection postCol = jongo.getCollection("post");

        commentCol.update("{'comments._id':#}", commentId)
            .with("{$addToSet:{'comments.$.likes':#}}", userId);

        Post post = postCol.findAndModify("{'comments._id':#}", commentId)
            .with("{$addToSet:{'comments.$.likes':#}}", userId).projection("{_id:1}")
            .as(Post.class);

        if (post != null)
            expire("post:" + post.getId());
    }

    public static void unlike(ObjectId commentId, ObjectId userId) {
        MongoCollection postCol = jongo.getCollection("post");
        MongoCollection commentCol = jongo.getCollection("comment");

        commentCol.update("{'comments._id':#}", commentId)
            .with("{$pull:{'comments.$.likes':#}}", userId);

        Post post = postCol.findAndModify("{'comments._id':#}", commentId)
            .with("{$pull:{'comments.$.likes':#}}", userId).projection("{_id:1}")
            .as(Post.class);

        if (post != null)
            expire("post:" + post.getId());
    }
}
