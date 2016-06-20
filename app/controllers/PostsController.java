package controllers;

import controllers.constants.Error;

import models.Comment;
import models.Feed;
import models.Post;
import models.User;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;

public class PostsController extends AppController {
    public static Result getFeed(JsonNode params) {
        User me = getMe(params);
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(Feed.get(me.getId(), new Date(until), limit));
    }

    public static Result getTimeline(JsonNode params) {
        ObjectId myId = params.has("access_token") ? getMe(params).getId() : null;
        ObjectId userId = getObjectId(params, "user_id");
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        User user = User.get(userId);
        if (user == null)
            return NotFound();

        return Ok(Post.getTimeline(user.getId(), myId, new Date(until), limit, false));
    }

    public static Result add(JsonNode params) {
        User me = getMe(params);
        String text = params.has("text") ? params.get("text").textValue() : null;

        Post post = new Post(me.getId(), text, getAttachments(params));
        post.save(me);

        return Ok(post);
    }

    public static Result get(JsonNode params) {
        ObjectId myId = params.has("access_token") ? getMe(params).getId() : null;
        ObjectId postId = getObjectId(params, "post_id");

        Post post = Post.get(postId);

        if (post == null || post.getDeleted() != null)
            return NotFound();

        post.postproduct(myId);

        return Ok(post);
    }

    public static Result getComment(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 50;

        return Ok(Comment.get(postId, me.getId(), new Date(until), limit));
    }

    public static Result addComment(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");
        String text = params.has("text") ? params.get("text").textValue() : null;

        Comment comment = new Comment(me.getId(), text, getAttachments(params));
        comment.save(postId);

        return Ok(comment);
    }

    public static Result like(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");

        Post.like(postId, me.getId());

        return Ok();
    }

    public static Result unlike(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");

        Post.unlike(postId, me.getId());

        return Ok();
    }

    public static Result delete(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");

        Post.delete(postId, me.getId());

        return Ok();
    }

    public static Result likeComment(JsonNode params) {
        User me = getMe(params);
        ObjectId commentId = getObjectId(params, "comment_id");

        Comment.like(commentId, me.getId());

        return Ok();
    }

    public static Result unlikeComment(JsonNode params) {
        User me = getMe(params);
        ObjectId commentId = getObjectId(params, "comment_id");

        Comment.unlike(commentId, me.getId());

        return Ok();
    }

    public static Result deleteComment(JsonNode params) {
        User me = getMe(params);
        ObjectId commentId = getObjectId(params, "comment_id");

        Comment.delete(commentId, me.getId());

        return Ok();
    }

    public static Result getRequest(JsonNode params) {
        User me = getMe(params);
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(Post.getTimeline(me.getId(), null, new Date(until), limit, true));
    }

    public static Result addRequest(JsonNode params) {
        User me = getMe(params);
        String type = params.get("type").textValue();
        String text = params.has("text") ? params.get("text").textValue() : null;

        if (Post.isRequested(me.getId(), type))
            return Error(Error.REQUESTED);

        Post post = new Post(me.getId(), text, getAttachments(params));
            post.setType(type);
            post.setMetadata(params.get("metadata"));
            post.setStatus("apply");
        post.save();

        return Ok(post);
    }

    public static Result updateRequest(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");

        Post.updateRequest(me.getId(), postId, params.get("metadata"));

        return Ok();
    }

    public static Result cancelRequest(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");

        Post.cancelRequest(me.getId(), postId);

        return Ok();
    }
}
