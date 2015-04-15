package controllers;

import controllers.annotations.*;

import com.fasterxml.jackson.databind.JsonNode;

public class TestController extends AppController {
    @Anonymous
    public static Result echo(JsonNode params) {
        return Ok(params);
    }
}
