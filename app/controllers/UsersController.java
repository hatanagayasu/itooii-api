package controllers;

import play.*;
import play.mvc.*;

import controllers.annotations.*;
import controllers.constants.Error;

import models.User;

import com.fasterxml.jackson.databind.JsonNode;

public class UsersController extends AppController
{
    public static Result me(JsonNode json)
    {
        return ok("me");
    }

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
}
