package controllers;

import play.*;
import play.mvc.*;

import controllers.annotations.*;
import controllers.constants.Error;

import com.fasterxml.jackson.databind.JsonNode;

public class UsersController extends AppController
{
    public static Result me(JsonNode json)
    {
        return ok("me");
    }

    @Validation(name="email", rule=EMAIL)
    @Validation(name="password", rule=NOT_EMPTY)
    public static Result add(JsonNode json)
    {
        return ok("add");
    }
}
