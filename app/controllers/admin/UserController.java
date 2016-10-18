
package controllers.admin;

import controllers.Result;
import controllers.constants.Error;
import models.Model;

import models.User;
import models.admin.UserManage;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class UserController extends AppController {
    
    public static Result getCount(JsonNode params) {
        
        String filter = "{}";
        if(params.get("dbquery") != null)
        {
            filter = params.get("dbquery").textValue();
        }

        long count = UserManage.getCount(filter);

        ObjectNode result = mapper.createObjectNode();
        result.put("count", count);

        return Ok(result);
    }
    
    public static Result get(JsonNode params) {
        String filter = "";
        if(params.get("dbquery") != null)
        {
            filter = params.get("dbquery").textValue() + ",";
        }
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        ObjectNode result = mapper.createObjectNode();
        //ArrayNode usersNode = mapper.createArrayNode();
        
        List<User> users = UserManage.get(filter, new Date(until), limit);
        long lastCreateTime = 0;
        if( users.size() > 0 ){
            User lastUser = users.get(users.size() - 1);
            lastCreateTime = lastUser.getCreatedTime().getTime();
        }
        
        result.put("users", Model.toJson(users).toString());
        result.put("nextQuery", lastCreateTime);
        
        return Ok(result);
    }
    
    public static Result suspend(JsonNode params) {
        ObjectId userId = getObjectId(params, "userid");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        ObjectNode userparams = mapper.createObjectNode();
        userparams.put("privilege", 10);  
        userparams.put("metadata", "{ suspend : true }");
        user.update(userparams);
        
        return Ok();
    }
    
    public static Result unsuspend(JsonNode params) {
        ObjectId userId = getObjectId(params, "userid");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        ObjectNode userparams = mapper.createObjectNode();
        userparams.put("privilege", 20);  
        userparams.put("metadata", "{ suspend : false }");
        user.update(userparams);
        
        return Ok();
    }
}