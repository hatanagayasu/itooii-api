package controllers;

import play.*;
import play.mvc.*;

import controllers.constants.Error;

import models.Model;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

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

    public static Map<String,Object> context()
    {
        return ctx().args;
    }

    public static Results.Status ok(Model model)
    {
        return ok(Model.toJson(model));
    }

    public static Results.Status error(Error error)
    {
        ObjectNode result = mapper.createObjectNode();
        result.put("error_code", error.getCode());
        result.put("description", error.getDescription());

        return status(error.getCode(), result);
    }

    public static Results.Status error(Error error, Object... params)
    {
        ObjectNode result = mapper.createObjectNode();
        result.put("error_code", error.getCode());
        result.put("description", String.format(error.getDescription(), params));

        return status(error.getCode(), result);
    }

    public static void errorlog(Throwable cause)
    {
        StringWriter errors = new StringWriter();
        cause.printStackTrace(new PrintWriter(errors));
        Logger.error(errors.toString());
    }
}
