package controllers;

import controllers.constants.Error;

import models.Chat;
import models.Message;
import models.User;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import org.bson.types.ObjectId;

public class MessagesController extends AppController {
    public static Result get(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        ObjectId chatId = Chat.getChatId(me.getId(), userId);

        return Ok(Message.get(chatId, until, limit));
    }

    public static Result add(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);
        String text = params.has("text") ? params.get("text").textValue() : null;

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (user.getBlockings() != null && user.getBlockings().contains(me.getId()))
            return Error(Error.OBJECT_FORBIDDEN);

        ObjectId chatId = Chat.getChatId(me.getId(), userId);

        Message message = new Message(me.getId(), text, getAttachments(params));
        message.save(chatId);

        sendEvent(userId, message);
        sendEvent(me.getId(), message);

        return Ok(message);
    }

    public static Result list(JsonNode params) {
        User me = getMe(params);
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(Chat.list(me.getId(), new Date(until), limit));
    }
}
