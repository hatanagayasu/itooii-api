package controllers;

import models.User;

import com.fasterxml.jackson.databind.JsonNode;

public class PushNotificationController extends AppController {
    public static Result addGCM(JsonNode params) {
        User me = getMe(params);
        String id = params.get("id").textValue();
        String lang = params.get("lang").textValue();

        me.addGCM(id, lang);

        return Ok();
    }

    public static Result updateGCM(JsonNode params) {
        User me = getMe(params);
        String id = params.get("id").textValue();
        String lang = params.get("lang").textValue();

        me.updateGCM(id, lang);

        return Ok();
    }

    public static Result removeGCM(JsonNode params) {
        User me = getMe(params);
        String id = params.get("id").textValue();

        me.removeGCM(id);

        return Ok();
    }
}
