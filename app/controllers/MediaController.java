package controllers;

import play.*;

import controllers.annotations.*;
import controllers.constants.Error;

import java.io.File;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MediaController extends AppController
{
    public static Result upload(JsonNode params)
    {
        File file = request().body().asRaw().asFile();

        return Ok();
    }
}
