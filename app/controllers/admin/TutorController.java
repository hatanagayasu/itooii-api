package controllers.admin;

import controllers.Result;
import controllers.constants.Error;
import models.Comment;
import models.Model;
import models.Post;
import models.User;
import models.admin.PostManage;
import models.admin.TutorManage;
import models.admin.UserManage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TutorController extends AppController{

    public static Result getCount(JsonNode params) {
        
        String filter = "{'type' : 'tutor'}";
        /*if(params.get("dbquery") != null)
        {
            filter = params.get("dbquery").textValue();
        }*/

        long count = PostManage.getCount(filter);

        ObjectNode result = mapper.createObjectNode();
        result.put("count", count);

        return Ok(result);
    }
    
    public static Result get(JsonNode params) {
        String filter = "'type' : 'tutor',";
        if(params.get("dbquery") != null)
        {
            filter = params.get("dbquery").textValue() + ",";
        }
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        ObjectNode result = mapper.createObjectNode();
        //ArrayNode usersNode = mapper.createArrayNode();
        
        List<Post> posts = TutorManage.get(filter, new Date(until), limit);
        long lastCreateTime = 0;
        if( posts.size() > 0 ){
            Post lastPost = posts.get(posts.size() - 1);
            lastCreateTime = lastPost.getCreated().getTime();//Post.getCreatedTime().getTime();
        }
        
        result.put("users", Model.toJson(posts).toString());
        result.put("nextQuery", lastCreateTime);
        
        return Ok(result);
    }
    
    public static Result post(JsonNode params) {
        ObjectId postId = getObjectId(params, "postid");
        Post post = (Post) Post.get(postId);

        if (post == null)
            return Error(Error.USER_NOT_FOUND);

        //TutorManage tutor = (TutorManage)post;
        TutorManage.update(postId, params);
        
        return Ok();
    }
}
