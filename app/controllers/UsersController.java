package controllers;

import play.*;

import controllers.annotations.*;
import controllers.constants.Error;

import models.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class UsersController extends AppController
{
    public static Result me(JsonNode params)
    {
        String token = params.get("access_token").textValue();

        User user = User.getByToken(token);
        if (user == null)
            return Error(Error.INVALID_ACCESS_TOKEN);

        user.setPassword(null);

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

        return Ok(user);
    }

    @Validation(name="email", rule="email")
    @Validation(name="password")
    @Validation(name="name")
    @Validation(name="native_language", type="array", rule="minSize=1")
    @Validation(name="native_language[]", type="integer")
    @Validation(name="practice_language", type="array", rule="minSize=1")
    @Validation(name="practice_language[]", type="integer")
    public static Result update(JsonNode params)
    {
        User.update(params);

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

        if (!user.matchPassword(password))
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
