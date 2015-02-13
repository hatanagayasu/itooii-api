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
    @Anonymous
    public static Result me(JsonNode json)
    {
        return ok("me");
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

        User user = User.add(email, password, name);

        return ok(user);
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
            return error(Error.INCORRECT_USER);

        if (!user.matchPassword(password))
            return error(Error.INCORRECT_PASSWORD);

        ObjectNode result = mapper.createObjectNode();
        result.put("access_token", user.newToken());

        return ok(result);
    }

    public static Result logout(JsonNode json)
    {
        String token = json.get("access_token").textValue();
        User.deleteToken(token);

        return ok();
    }
}
