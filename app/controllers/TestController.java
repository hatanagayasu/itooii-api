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
}
