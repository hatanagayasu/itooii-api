package controllers;

import com.fasterxml.jackson.databind.JsonNode;

public class TestController extends AppController {
    public static Result echo(JsonNode params) {
        return Ok(params);
    }
}
