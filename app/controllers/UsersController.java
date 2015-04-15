package controllers;

import controllers.annotations.*;
import controllers.constants.Error;

import models.PracticeLanguage;
import models.User;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;

public class UsersController extends AppController {
    public static Result me(JsonNode params) {
        User user = getMe(params);
        if (user == null)
            return Error(Error.INVALID_ACCESS_TOKEN);

        return Ok(user);
    }

    @Anonymous
    @Validation(name = "user_id", type = "id", require = true)
    public static Result get(JsonNode params) {
        ObjectId userId = getObjectId(params, "user_id");

        User user = User.get(userId);
        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        return Ok(user);
    }

    @Anonymous
    @Validation(name = "email", rule = "email", require = true)
    public static Result exist(JsonNode params)
    {
        String email = params.get("email").textValue();

        return User.getByEmail(email) != null ? Ok() : Error(Error.NOT_FOUND);
    }

    @Anonymous
    @Validation(name = "email", rule = "email", require = true)
    @Validation(name = "password", require = true)
    @Validation(name = "name", require = true)
    @Validation(name = "native_language", type = "array", rule = "minSize=1,maxSize=2", require = true)
    @Validation(name = "native_language[]", type = "integer", require = true)
    @Validation(name = "practice_language", type = "array", rule = "minSize=1,maxSize=4", require = true)
    @Validation(name = "practice_language[]", type = "object")
    @Validation(name = "practice_language[].id", type = "integer", require = true)
    @Validation(name = "practice_language[].level", type = "integer", require = true)
    public static Result add(JsonNode params) {
        String email = params.get("email").textValue();

        if (User.getByEmail(email) != null)
            return Error(Error.USER_ALREADY_EXISTS);

        String password = params.get("password").textValue();
        String name = params.get("name").textValue();

        Iterator<JsonNode> values = params.get("native_language").iterator();
        List<Integer> nativeLanguage = new ArrayList<Integer>();
        while (values.hasNext())
            nativeLanguage.add(values.next().intValue());

        values = params.get("practice_language").iterator();
        List<PracticeLanguage> practiceLanguage = new ArrayList<PracticeLanguage>();
        while (values.hasNext()) {
            JsonNode value = values.next();
            int id = value.get("id").intValue();
            int level = value.get("level").intValue();
            practiceLanguage.add(new PracticeLanguage(id, level));
        }

        User user = new User(email, password, name, nativeLanguage, practiceLanguage);
        user.save();

        return Ok(user);
    }

    @Validation(name = "email", rule = "email")
    @Validation(name = "password")
    @Validation(name = "name")
    @Validation(name = "native_language", type = "array", rule = "minSize=1,maxSize=2")
    @Validation(name = "native_language[]", type = "integer")
    @Validation(name = "practice_language", type = "array", rule = "minSize=1,maxSize=4")
    @Validation(name = "practice_language[]", type = "object")
    @Validation(name = "practice_language[].id", type = "integer", require = true)
    @Validation(name = "practice_language[].level", type = "integer", require = true)
    public static Result update(JsonNode params) {
        User me = getMe(params);
        me.update(params);

        return Ok();
    }

    @Validation(name = "user_id", type = "id", require = true)
    public static Result follow(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (me.getFollowings() == null || !me.getFollowings().contains(userId))
            me.follow(userId);

        return Ok();
    }

    @Validation(name = "user_id", type = "id", require = true)
    public static Result unfollow(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (me.getFollowings() != null && me.getFollowings().contains(userId))
            me.unfollow(userId);

        return Ok();
    }

    @Anonymous
    @Validation(name = "email", rule = "email", require = true)
    @Validation(name = "password", require = true)
    public static Result login(JsonNode params) {
        String email = params.get("email").textValue();
        String password = params.get("password").textValue();

        User user = User.getByEmail(email);

        if (user == null)
            return Error(Error.INCORRECT_USER);

        if (!user.getPassword().equals(models.Model.md5(password)))
            return Error(Error.INCORRECT_PASSWORD);

        ObjectNode result = mapper.createObjectNode();
        result.put("access_token", user.newToken());

        return Ok(result);
    }

    public static Result logout(JsonNode params) {
        String token = params.get("access_token").textValue();

        User.deleteToken(token);

        return Ok();
    }

    @Anonymous
    public static Result search(JsonNode params) {
        return Ok(User.search());
    }
}
