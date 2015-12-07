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
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    @Postproduct
    private ObjectId userId;
    private String text;
    private List<Attachment> attachments;
    private Date created;
    @JsonProperty("like_count")
    private int likeCount;
    private Set<ObjectId> likes;
    @JsonIgnore
    private boolean liked;
    private Boolean deleted;

    public Comment() {
    }

    public Comment(ObjectId userId, String text, List<Attachment> attachments) {
        this.id = new ObjectId();
        this.userId = userId;
        this.text = text;
        this.attachments = attachments == null ? null :
            (attachments.isEmpty() ? null : attachments);
        this.created = new Date();
    }

    public void save(ObjectId postId) {
        MongoCollection postCol = jongo.getCollection("post");
        MongoCollection commentCol = jongo.getCollection("comment");

        Post post = postCol
            .findAndModify("{_id:#}", postId)
            .with("{$inc:{comment_count:1},$addToSet:{commentators:#}" +
                ",$push:{comments:{$each:[#],$slice:-4}}}", userId, this)
            .projection("{user_id:1,comment_count:1,commentators:1,likes:1}")
            .as(Post.class);

        if (post == null)
            return;

        int page = post.getCommentCount() / 50;
        commentCol.update("{post_id:#,page:#}", postId, page).upsert()
            .with("{$push:{comments:#},$setOnInsert:{user_id:#,created:#}}",
                this, post.getUserId(), this.created);

        del("post:" + postId);

        if (!userId.equals(post.getUserId())) {
            new Activity(userId, ActivityType.commentYourPost, postId, post.getUserId()).queue();
        }
        Set<ObjectId> likes = post.getLikes();
        if (userId.equals(post.getUserId()) && likes != null) {
            likes.remove(userId);
            new Activity(userId, ActivityType.ownerCommentPostYouLike, postId, likes).queue();
        } else {
            Set<ObjectId> commentators = post.getCommentators();
            if (commentators != null) {
                commentators.remove(userId);
                commentators.remove(post.getUserId());
                new Activity(userId, ActivityType.commentPostYouComment, postId, commentators)
                    .queue();
            }
        }
    }

    public static Page get(ObjectId postId, ObjectId userId, Date until, int limit) {
        MongoCollection commentCol = jongo.getCollection("comment");
        String previous = null;

        MongoCursor<Comments> cursor = commentCol
            .find("{post_id:#,created:{$lt:#}}", postId, until)
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
        int count = 0;
        for (int i = commentses.size() - 1; i >= 0; i--) {
            for (Comment comment : commentses.get(i).getComments()) {
                count++;
                if (comment.deleted == null && comment.created.before(until))
                    comments.add(comment);
            }
        }

        if (comments.size() > limit)
            comments.subList(0, comments.size() - limit).clear();

        postproduct(comments, userId);

        if (count == limit) {
            previous = String.format("until=%d&limit=%d",
                comments.get(0).getCreated().getTime(), limit);
        }

        return new Page(comments, previous);
    }

    public static void postproduct(List<Comment> comments, ObjectId userId) {
        for (Comment comment : comments) {
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

        Comments comments = commentCol.findAndModify("{'comments._id':#}", commentId)
            .with("{$addToSet:{'comments.$.likes':#},$inc:{'comments.$.like_count':1}}", userId)
            .projection("{comments:{$elemMatch:{_id:#}}}", commentId)
            .as(Comments.class);

        if (comments == null)
            return;

        Post post = postCol.findAndModify("{'comments._id':#}", commentId)
            .with("{$addToSet:{'comments.$.likes':#},$inc:{'comments.$.like_count':1}}", userId)
            .projection("{_id:1}")
            .as(Post.class);

        if (post != null) {
            del("post:" + post.getId());

            ObjectId commentator = comments.getComments().get(0).getUserId();
            if (!userId.equals(commentator)) {
                new Activity(userId, ActivityType.likeYourComment, post.getId(), commentator)
                    .queue();
            }
        }
    }

    public static void unlike(ObjectId commentId, ObjectId userId) {
        MongoCollection postCol = jongo.getCollection("post");
        MongoCollection commentCol = jongo.getCollection("comment");

        commentCol.update("{'comments._id':#}", commentId)
            .with("{$pull:{'comments.$.likes':#}}", userId);

        Post post = postCol.findAndModify("{'comments._id':#}", commentId)
            .with("{$pull:{'comments.$.likes':#},$inc:{'comments.$.like_count':-1}}", userId)
            .projection("{_id:1}")
            .as(Post.class);

        if (post != null)
            del("post:" + post.getId());
    }

    public static void delete(ObjectId commentId, ObjectId userId) {
        MongoCollection postCol = jongo.getCollection("post");
        MongoCollection commentCol = jongo.getCollection("comment");

        Comments comments = commentCol
            .findAndModify("{comments:{$elemMatch:{_id:#,user_id:#}}}", commentId, userId)
            .with("{$set:{'comments.$.deleted':#}}", true)
            .projection("{post_id:1}")
            .as(Comments.class);

        if (comments == null) {
            // a post owner can delete a another's comments
            comments = commentCol
                .findAndModify("{user_id:#,comments:{$elemMatch:{_id:#}}}", userId, commentId)
                .with("{$set:{'comments.$.deleted':#}}", true)
                .projection("{post_id:1}")
                .as(Comments.class);

            if (comments == null)
                return;
        }

        postCol.update(comments.getPostId())
            .with("{$inc:{comment_count:-1},$pull:{comments:{_id:#}}}", commentId);

        del("post:" + comments.getPostId());
    }
}
