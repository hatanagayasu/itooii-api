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
public class Comment extends Model
{
    @Id
    private ObjectId id;
    @JsonProperty("post_id")
    private ObjectId postId;
    @JsonProperty("user_id")
    private ObjectId userId;
    private String text;
    private List<Attachment> attachments;
    private Date created;
    @JsonProperty("like_count")
    private int likeCount;
    private Set<ObjectId> likes;

    public Comment()
    {
    }

    public Comment(ObjectId postId, ObjectId userId, String text, List<Attachment> attachments)
    {
        this.id = new ObjectId();
        this.postId = postId;
        this.userId = userId;
        this.text = text;
        this.attachments = attachments;
        this.created = new Date();
        this.likeCount = 0;
    }

    public void save()
    {
        MongoCollection postCol = jongo.getCollection("post");
        MongoCollection commentCol = jongo.getCollection("comment");

        Post post = postCol.findAndModify("{_id:#}", postId)
            .projection("{comment_count:1}")
            .with("{$inc:{comment_count:1}}").as(Post.class);

        if (post == null)
            return;

        int page = post.getCommentCount() / 2;
        commentCol.update("{post_id:#,page:#}", postId, page).upsert()
            .with("{$inc:{count:1},$push:{comments:#}}", this);
    }

    public static List<Comment> get(ObjectId postId)
    {
        MongoCollection commentCol = jongo.getCollection("comment");

        MongoCursor<Comments> cursor = commentCol.find("{post_id:#}", postId)
            .sort("{page:1}").as(Comments.class);

        List<Comment> comments = new ArrayList<Comment>();
        while (cursor.hasNext())
        {
            for (Comment comment : cursor.next().getComments())
               comments.add(comment);
        }

        try
        {
            cursor.close();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return comments;
    }

    public static void like(ObjectId commentId, ObjectId userId)
    {
        MongoCollection commentCol = jongo.getCollection("comment");

        commentCol.update("{'comments._id':#,'comments.likes':{$ne:#}}", commentId, userId)
            .with("{$addToSet:{'comments.$.likes':#},$inc:{'comments.$.like_count':1}}", userId);
    }

    public static void unlike(ObjectId commentId, ObjectId userId)
    {
        MongoCollection commentCol = jongo.getCollection("comment");

        commentCol.update("{'comments._id':#,'comments.likes':#}", commentId, userId)
            .with("{$pull:{'comments.$.likes':#},$inc:{'comments.$.like_count':-1}}", userId);
    }
}
