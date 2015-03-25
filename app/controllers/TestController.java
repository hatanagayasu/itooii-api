package controllers;

import java.util.Iterator;

import controllers.annotations.*;

import org.bson.types.ObjectId;

import com.fasterxml.jackson.databind.JsonNode;

public class TestController extends AppController {
    @Anonymous
    public static Result echo(JsonNode params) {
        return Ok(params);
    }

    @Anonymous
    @Validation(name = "user_id", type = "array", require = true)
    @Validation(name = "user_id[]", type = "string", require = true)
    public static Result ready(JsonNode params) {
        Iterator<JsonNode> values = params.get("user_id").iterator();
        while (values.hasNext())
            inPairQueue(new ObjectId(values.next().textValue()));

        return Ok(params);
    }
}
