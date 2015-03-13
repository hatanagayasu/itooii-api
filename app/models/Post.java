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
public class Post extends Model
{
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    private String text;
    private List<Attachment> attachments;
    private Date created;

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

        MongoCursor<Post> posts = postCol.find("{user_id:{$in:#}}", ids)
            .sort("{created:-1}").as(Post.class);
        List<Post> result = new ArrayList<Post>();
        while (posts.hasNext())
            result.add(posts.next());

        return result;
    }

    public static void like(ObjectId postId, ObjectId userId)
    {
        MongoCollection postCol = jongo.getCollection("post");

        postCol.update(postId).with("{$addToSet:{likes:#}}", userId);
    }

    public static void unlike(ObjectId postId, ObjectId userId)
    {
        MongoCollection postCol = jongo.getCollection("post");

        postCol.update(postId).with("{$pull:{likes:#}}", userId);
    }
}
