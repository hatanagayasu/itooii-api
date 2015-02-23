package controllers;

import play.*;
import play.mvc.*;

import controllers.constants.Error;

import models.Model;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import org.bson.types.ObjectId;

public class AppController extends Controller
{
    public static final ObjectMapper mapper = new ObjectMapper();

    public static Result Ok()
    {
        return new Result(200);
    }

    public static Result Ok(Model model)
    {
        return new Result(200, Model.toJson(model));
    }

    public static Result Ok(JsonNode node)
    {
        return new Result(200, node);
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

        return new Result(400, result);
    }

    public static Result Error(Error error, Object... params)
    {
        ObjectNode result = mapper.createObjectNode();
        result.put("error_code", error.getCode());
        result.put("description", String.format(error.getDescription(), params));

        return new Result(400, result);
    }

    public static Result NotFound()
    {
        return new Result(404);
    }

    public static void errorlog(Object error)
    {
        Logger.error(error.toString());
    }

    public static void errorlog(Throwable cause)
    {
        StringWriter errors = new StringWriter();
        cause.printStackTrace(new PrintWriter(errors));
        Logger.error(errors.toString());
    }

    public static ObjectId getObjectId(JsonNode params, String name)
    {
        return (ObjectId)((POJONode)params.get(name)).getPojo();
    }
}
