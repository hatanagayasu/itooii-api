package controllers;

import play.*;
import play.mvc.*;

import controllers.annotations.*;
import controllers.constants.Error;

import models.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class UsersController extends AppController
{
    public static Result me(JsonNode json)
    {
        User user = (User)context().get("me");

        return Ok(user);
    }

    @Anonymous
    @Validation(name="email", rule=EMAIL)
    public static Result exist(JsonNode json)
    {
        String email = json.get("email").textValue();
        User user = User.getByEmail(email);

        return user != null ? Ok(user) : NotFound();
    }

    @Anonymous
    @Validation(name="email", rule=EMAIL)
    @Validation(name="password", rule=NOT_EMPTY)
    @Validation(name="name", rule=NOT_EMPTY)
    public static Result add(JsonNode json)
    {
        String email = json.get("email").textValue();
        String password = json.get("password").textValue();
        String name = json.get("name").textValue();

        if (User.getByEmail(email) != null)
            return Error(Error.USER_ALREADY_EXISTS);

        User user = User.add(email, password, name);

        return Ok(user);
    }

    @Anonymous
    @Validation(name="email", rule=EMAIL)
    @Validation(name="password", rule=NOT_EMPTY)
    public static Result login(JsonNode json)
    {
        String email = json.get("email").textValue();
        String password = json.get("password").textValue();

        User user = User.getByEmail(email);

        if (user == null)
            return Error(Error.INCORRECT_USER);

        if (!user.matchPassword(password))
            return Error(Error.INCORRECT_PASSWORD);

        ObjectNode result = mapper.createObjectNode();
        result.put("access_token", user.newToken());

        return Ok(result);
    }

    public static Result logout(JsonNode json)
    {
        String token = json.get("access_token").textValue();
        User.deleteToken(token);

        return Ok();
    }
}
