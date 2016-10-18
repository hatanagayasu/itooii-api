package controllers.admin;

import controllers.Result;
import controllers.constants.Error;
import models.Comment;
import models.Model;
import models.Post;
import models.User;
import models.admin.PostManage;
import models.admin.UserManage;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class PostController extends AppController{
    
    public static Result get(JsonNode params) {
        //ObjectId myId = params.has("access_token") ? getMe(params).getId() : null;
        ObjectId postId = getObjectId(params, "postid");

        Post post = Post.get(postId);

        if (post == null )
            return NotFound();

        //post.postproduct(myId);

        return Ok(post);
    }
    
    public static Result delete(JsonNode params) {
        ObjectId postId = getObjectId(params, "postid");

        PostManage.delete(postId);

        return Ok();

    }
    
    public static Result deleteComment(JsonNode params) {
        User me = getMe(params);
        ObjectId commentId = getObjectId(params, "comment_id");

        PostManage.deleteComment(commentId);

        return Ok();
    }
}
