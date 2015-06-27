package controllers;

import models.Activity;
import models.User;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ActivitiesController extends AppController {
    public static Result getBadges(JsonNode params) {
        User me = getMe(params);

        ObjectNode result = mapper.createObjectNode();

        result.put("notifications", Activity.getUnreadNotificationCount(me));

        return Ok(result);
    }

    public static Result getNotifications(JsonNode params) {
        User me = getMe(params);
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(Activity.getNotifications(me, new Date(until), limit));
    }
}
