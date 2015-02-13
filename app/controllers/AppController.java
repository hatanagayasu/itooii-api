package controllers;

import play.*;
import play.mvc.*;

import controllers.constants.Error;

import models.Model;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AppController extends Controller
{
    public static final ObjectMapper mapper = new ObjectMapper();

    public static final String
        ALPHA_NUMERIC = "ALPHA_NUMERIC",
        BOOLEAN = "BOOLEAN",
        EMAIL = "EMAIL",
        NOT_EMPTY = "NOT_EMPTY",
        UUID = "UUID";

    public static Result Ok()
    {
        return new Result(200);
    }

    public static Result Ok(Model model)
    {
        return new Result(200, Model.toJson(model));
    }

    public static Result Ok(ObjectNode node)
    {
        return new Result(200, node);
    }

    public static Result Error(Error error)
    {
        ObjectNode result = mapper.createObjectNode();
        result.put("error_code", error.getCode());
        result.put("description", error.getDescription());

        return new Result(error.getCode(), result);
    }

    public static Result Error(Error error, Object... params)
    {
        ObjectNode result = mapper.createObjectNode();
        result.put("error_code", error.getCode());
        result.put("description", String.format(error.getDescription(), params));

        return new Result(error.getCode(), result);
    }

    public static Result NotFound()
    {
        return new Result(404);
    }

    public static void errorlog(Throwable cause)
    {
        StringWriter errors = new StringWriter();
        cause.printStackTrace(new PrintWriter(errors));
        Logger.error(errors.toString());
    }
}
