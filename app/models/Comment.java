package models;

import models.User;

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
    }

    public void save()
    {
        MongoCollection commentCol = jongo.getCollection("comment");
        commentCol.save(this);
    }

    public static Comment get(ObjectId commentId)
    {
        MongoCollection commentCol = jongo.getCollection("comment");

        Comment comment = commentCol.findOne(commentId).as(Comment.class);

        return comment;
    }

    public static List<Comment> getByPostId(ObjectId postId)
    {
        MongoCollection commentCol = jongo.getCollection("comment");

        MongoCursor<Comment> cursor = commentCol.find("{post_id:#}", postId)
            .sort("{created:-1}").as(Comment.class);
        List<Comment> comments = new ArrayList<Comment>();
        while (cursor.hasNext())
            comments.add(cursor.next());

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

        commentCol.update(commentId).with("{$addToSet:{likes:#}}", userId);
    }

    public static void unlike(ObjectId commentId, ObjectId userId)
    {
        MongoCollection commentCol = jongo.getCollection("comment");

        commentCol.update(commentId).with("{$pull:{likes:#}}", userId);
    }
}
