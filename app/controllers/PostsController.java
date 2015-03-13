package controllers;

import play.*;

import controllers.annotations.*;
import controllers.constants.Error;

import models.Attachment;
import models.Comment;
import models.Post;
import models.User;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;

public class PostsController extends AppController
{
    public static Result getFeed(JsonNode params)
    {
        User me = getMe(params);

        List<Post> feed = Post.getFeed(me);

        return Ok(feed);
    }

    @Validation(name="text", require=true)
    @Validation(name="attachments", type="array")
    @Validation(name="attachments[]", type="object")
    @Validation(name="attachments[].type", rule="(photo|url)", require=true)
    @Validation(name="attachments[].photo_id", type="id", depend="type=photo")
    @Validation(name="attachments[].preview", depend="type=url")
    public static Result add(JsonNode params)
    {
        User me = getMe(params);
        String text = params.get("text").textValue();

        List<Attachment> attachments = new ArrayList<Attachment>();
        if (params.has("attachments"))
        {
            Iterator<JsonNode> values = params.get("attachments").iterator();
            while(values.hasNext())
            {
                JsonNode attachment = values.next();
                String type = attachment.get("type").textValue();
                if (attachment.has("photo_id"))
                    attachments.add(new Attachment(type, getObjectId(attachment, "photo_id")));
                else if (attachment.has("preview"))
                    attachments.add(new Attachment(type, attachment.get("preview").textValue()));
            }
        }

        Post post = new Post(me.getId(), text, attachments);
        post.save();

        return Ok(post);
    }

    @Validation(name="post_id", type="id", require=true)
    public static Result get(JsonNode params)
    {
        ObjectId postId = getObjectId(params, "post_id");
        Post post = Post.get(postId);

        return Ok(post);
    }

    @Validation(name="post_id", type="id", require=true)
    public static Result getComment(JsonNode params)
    {
        ObjectId postId = getObjectId(params, "post_id");

        List<Comment> comments = Comment.getByPostId(postId);

        return Ok(comments);
    }

    @Validation(name="post_id", type="id", require=true)
    @Validation(name="text", require=true)
    @Validation(name="attachments", type="array")
    @Validation(name="attachments[]", type="object")
    @Validation(name="attachments[].type", rule="(photo|url)", require=true)
    @Validation(name="attachments[].photo_id", type="id", depend="type=photo")
    @Validation(name="attachments[].preview", depend="type=url")
    public static Result addComment(JsonNode params)
    {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");
        String text = params.get("text").textValue();

        List<Attachment> attachments = new ArrayList<Attachment>();
        if (params.has("attachments"))
        {
            Iterator<JsonNode> values = params.get("attachments").iterator();
            while(values.hasNext())
            {
                JsonNode attachment = values.next();
                String type = attachment.get("type").textValue();
                if (attachment.has("photo_id"))
                    attachments.add(new Attachment(type, getObjectId(attachment, "photo_id")));
                else if (attachment.has("preview"))
                    attachments.add(new Attachment(type, attachment.get("preview").textValue()));
            }
        }

        Comment comment = new Comment(postId, me.getId(), text, attachments);
        comment.save();

        return Ok(comment);
    }

    @Validation(name="post_id", type="id", require=true)
    public static Result like(JsonNode params)
    {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");

        Post.like(postId, me.getId());

        return Ok();
    }

    @Validation(name="post_id", type="id", require=true)
    public static Result unlike(JsonNode params)
    {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");

        Post.unlike(postId, me.getId());

        return Ok();
    }

    @Validation(name="comment_id", type="id", require=true)
    public static Result likeComment(JsonNode params)
    {
        User me = getMe(params);
        ObjectId commentId = getObjectId(params, "comment_id");

        Comment.like(commentId, me.getId());

        return Ok();
    }

    @Validation(name="comment_id", type="id", require=true)
    public static Result unlikeComment(JsonNode params)
    {
        User me = getMe(params);
        ObjectId commentId = getObjectId(params, "comment_id");

        Comment.unlike(commentId, me.getId());

        return Ok();
    }
}
