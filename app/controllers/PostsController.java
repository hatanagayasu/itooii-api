package controllers;

import controllers.annotations.*;

import models.Comment;
import models.Feed;
import models.Post;
import models.User;

import com.fasterxml.jackson.databind.JsonNode;
import org.bson.types.ObjectId;

public class PostsController extends AppController {
    @Validation(name = "skip", type = "integer", rule = "min=0")
    @Validation(name = "until", type = "epoch")
    @Validation(name = "limit", type = "integer", rule = "min=1,max=25")
    public static Result getFeed(JsonNode params) {
        User me = getMe(params);
        int skip = params.has("skip") ? params.get("skip").intValue() : 0;
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        if (params.has("until") && !params.has("skip"))
            return Ok(Feed.get(me, until, limit));
        else
            return Ok(Feed.get(me, skip, until, limit));
    }

    @Validation(name = "text", depend = "|attachments")
    @Validation(name = "attachments", type = "array")
    @Validation(name = "attachments[]", type = "object")
    @Validation(name = "attachments[].type", rule = "(photo|url)", require = true)
    @Validation(name = "attachments[].photo_id", type = "id", depend = "type=photo")
    @Validation(name = "attachments[].preview", depend = "type=url")
    public static Result add(JsonNode params) {
        User me = getMe(params);
        String text = params.has("text") ? params.get("text").textValue() : null;

        Post post = new Post(me.getId(), text, getAttachments(params));
        post.save(me);

        return Ok(post);
    }

    @Validation(name = "post_id", type = "id", require = true)
    public static Result get(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObject(params, "post_id");

        Post post = Post.get(postId);

        if (post == null)
            return NotFound();

        post.postproduction(me.getId());

        return Ok(post);
    }

    @Validation(name = "post_id", type = "id", require = true)
    @Validation(name = "until", type = "epoch")
    @Validation(name = "limit", type = "integer", rule = "min=1,max=50")
    public static Result getComment(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObject(params, "post_id");
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 50;

        return Ok(Comment.get(postId, me.getId(), until, limit));
    }

    @Validation(name = "post_id", type = "id", require = true)
    @Validation(name = "text", depend = "|attachments")
    @Validation(name = "attachments", type = "array")
    @Validation(name = "attachments[]", type = "object")
    @Validation(name = "attachments[].type", rule = "(photo|url)", require = true)
    @Validation(name = "attachments[].photo_id", type = "id", depend = "type=photo")
    @Validation(name = "attachments[].preview", depend = "type=url")
    public static Result addComment(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObject(params, "post_id");
        String text = params.has("text") ? params.get("text").textValue() : null;

        Comment comment = new Comment(me.getId(), text, getAttachments(params));
        comment.save(postId);

        return Ok(comment);
    }

    @Validation(name = "post_id", type = "id", require = true)
    public static Result like(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObject(params, "post_id");

        Post.like(postId, me.getId());

        return Ok();
    }

    @Validation(name = "post_id", type = "id", require = true)
    public static Result unlike(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObject(params, "post_id");

        Post.unlike(postId, me.getId());

        return Ok();
    }

    @Validation(name = "comment_id", type = "id", require = true)
    public static Result likeComment(JsonNode params) {
        User me = getMe(params);
        ObjectId commentId = getObject(params, "comment_id");

        Comment.like(commentId, me.getId());

        return Ok();
    }

    @Validation(name = "comment_id", type = "id", require = true)
    public static Result unlikeComment(JsonNode params) {
        User me = getMe(params);
        ObjectId commentId = getObject(params, "comment_id");

        Comment.unlike(commentId, me.getId());

        return Ok();
    }
}
