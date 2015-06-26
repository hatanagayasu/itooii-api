package controllers;

import models.Activity;
import models.User;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;

public class ActivitiesController extends AppController {
    public static Result getNotifications(JsonNode params) {
        User me = getMe(params);
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(Activity.getNotifications(me.getId(), new Date(until), limit));
    }
}
