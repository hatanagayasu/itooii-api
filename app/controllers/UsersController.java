package controllers;

import play.*;

import controllers.annotations.*;
import controllers.constants.Error;

import models.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;

public class UsersController extends AppController
{
    public static Result me(JsonNode params)
    {
        User user = getMe(params);
        if (user == null)
            return Error(Error.INVALID_ACCESS_TOKEN);

        user.removePassword();

        return Ok(user);
    }

    @Anonymous
    @Validation(name="email", rule="email", require=true)
    @Validation(name="password", require=true)
    @Validation(name="native_language", type="array", rule="minSize=1", require=true)
    @Validation(name="native_language[]", type="integer", require=true)
    @Validation(name="practice_language", type="array", rule="minSize=1", require=true)
    @Validation(name="practice_language[]", type="integer", require=true)
    public static Result add(JsonNode params)
    {
        String email = params.get("email").textValue();

        if (User.getByEmail(email) != null)
            return Error(Error.USER_ALREADY_EXISTS);

        User user = User.add(params);
        user.removePassword();

        return Ok(user);
    }

    @Validation(name="email", rule="email")
    @Validation(name="password")
    @Validation(name="name")
    @Validation(name="native_language", type="array", rule="minSize=1")
    @Validation(name="native_language[]", type="integer")
    @Validation(name="practice_language", type="array", rule="minSize=1")
    @Validation(name="practice_language[]", type="integer")
    @Validation(name="following", type="array")
    @Validation(name="following[]", type="id")
    public static Result update(JsonNode params)
    {
        User me = getMe(params);
        me.update(params);

        return Ok();
    }

    @Validation(name="user_id", type="id", require=true)
    public static Result follow(JsonNode params)
    {
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.getById(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        User me = getMe(params);
        if (!me.containsFollowing(userId))
            me.follow(userId);

        return Ok();
    }

    @Validation(name="user_id", type="id", require=true)
    public static Result unfollow(JsonNode params)
    {
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.getById(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        User me = getMe(params);
        if (me.containsFollowing(userId))
            me.unfollow(userId);

        return Ok();
    }

    @Anonymous
    @Validation(name="email", rule="email", require=true)
    @Validation(name="password", require=true)
    public static Result login(JsonNode params)
    {
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

    public static Result logout(JsonNode params)
    {
        String token = params.get("access_token").textValue();

        User.deleteToken(token);

        return Ok();
    }
}
