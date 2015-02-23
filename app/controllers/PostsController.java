package controllers;

import play.*;

import controllers.annotations.*;
import controllers.constants.Error;

import models.Post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class PostsController extends AppController
{
    public static Result getFeed(JsonNode params)
    {
        return Ok(params);
    }

    @Validation(name="text", require=true)
    @Validation(name="attachments", type="array")
    @Validation(name="attachments[]", type="object")
    @Validation(name="attachments[].type", require=true)
    @Validation(name="attachments[].content", require=true)
    public static Result add(JsonNode params)
    {
        return Ok(params);
    }

    @Validation(name="post_id", type="id", require=true)
    public static Result getComment(JsonNode params)
    {
        return Ok(params);
    }

    @Validation(name="post_id", type="id", require=true)
    @Validation(name="text", require=true)
    @Validation(name="attachments", type="array")
    @Validation(name="attachments[]", type="object")
    @Validation(name="attachments[].type", require=true)
    @Validation(name="attachments[].content", require=true)
    public static Result addComment(JsonNode params)
    {
        return Ok(params);
    }

    @Validation(name="post_id", type="id", require=true)
    public static Result like(JsonNode params)
    {
        return Ok(params);
    }

    @Validation(name="post_id", type="id", require=true)
    public static Result unlike(JsonNode params)
    {
        return Ok(params);
    }

    @Validation(name="comment_id", type="id", require=true)
    public static Result likeComment(JsonNode params)
    {
        return Ok(params);
    }

    @Validation(name="comment_id", type="id", require=true)
    public static Result unlikeComment(JsonNode params)
    {
        return Ok(params);
    }
}
