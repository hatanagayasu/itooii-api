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
public class Post extends Model
{
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    private String text;
    private List<Attachment> attachments;
    private Date created;
    @JsonProperty("comment_count")
    private int commentCount;
    @JsonProperty("like_count")
    private int likeCount;
    private Set<ObjectId> likes;

    public Post()
    {
    }

    public Post(ObjectId userId, String text, List<Attachment> attachments)
    {
        this.id = new ObjectId();
        this.userId = userId;
        this.text = text;
        this.attachments = attachments;
        this.created = new Date();
        this.commentCount = 0;
        this.likeCount = 0;
    }

    public void save()
    {
        MongoCollection postCol = jongo.getCollection("post");
        postCol.save(this);
    }

    public static Post get(ObjectId postId)
    {
        MongoCollection postCol = jongo.getCollection("post");

        Post post = postCol.findOne(postId).as(Post.class);

        return post;
    }

    public static List<Post> getFeed(User user)
    {
        MongoCollection postCol = jongo.getCollection("post");

        Set<ObjectId> ids = new HashSet<ObjectId>();
        ids.addAll(user.getFollowers());
        ids.add(user.getId());

        MongoCursor<Post> cursor = postCol.find("{user_id:{$in:#}}", ids)
            .sort("{created:-1}").as(Post.class);
        List<Post> posts = new ArrayList<Post>();
        while (cursor.hasNext())
            posts.add(cursor.next());

        try
        {
            cursor.close();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return posts;
    }

    public static void like(ObjectId postId, ObjectId userId)
    {
        MongoCollection postCol = jongo.getCollection("post");

        postCol.update("{_id:#,likes:{$ne:#}}", postId, userId)
            .with("{$addToSet:{likes:#},$inc:{like_count:1}}", userId);
    }

    public static void unlike(ObjectId postId, ObjectId userId)
    {
        MongoCollection postCol = jongo.getCollection("post");

        postCol.update("{_id:#,likes:#}", postId, userId)
            .with("{$pull:{likes:#},$inc:{like_count:-1}}", userId);
    }
}
