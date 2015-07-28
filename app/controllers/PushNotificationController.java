package controllers;

import models.User;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;

public class PushNotificationController extends AppController {
    public static Result addGCM(JsonNode params) {
        User me = getMe(params);
        String id = params.get("id").textValue();

        me.addGCM(id);

        return Ok();
    }

    public static Result removeGCM(JsonNode params) {
        User me = getMe(params);
        String id = params.get("id").textValue();

        me.removeGCM(id);

        return Ok();
    }
}
