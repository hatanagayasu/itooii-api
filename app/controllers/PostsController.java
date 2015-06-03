package controllers;

import models.Comment;
import models.Feed;
import models.Post;
import models.User;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import org.bson.types.ObjectId;

public class PostsController extends AppController {
    public static Result getFeed(JsonNode params) {
        User me = getMe(params);
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(Feed.get(me.getId(), new Date(until), limit));
    }

    public static Result getTimeline(JsonNode params) {
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        User user;
        if (params.has("user_id")) {
            ObjectId userId = getObjectId(params, "user_id");
            user = User.get(userId);
            if (user == null)
                return NotFound();
        } else {
            user = getMe(params);
        }

        return Ok(Post.getTimeline(user.getId(), new Date(until), limit));
    }

    public static Result add(JsonNode params) {
        User me = getMe(params);
        String text = params.has("text") ? params.get("text").textValue() : null;

        Post post = new Post(me.getId(), text, getAttachments(params));
        post.save(me);

        return Ok(post);
    }

    public static Result get(JsonNode params) {
        User me = getMe(params);
        ObjectId postId = getObjectId(params, "post_id");

        Post post = Post.get(postId);

        if (post == null || post.getDeleted() != null)
            return NotFound();

        post.postproduct(me.getId());

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
}
