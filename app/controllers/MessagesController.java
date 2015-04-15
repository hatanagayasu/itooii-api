package controllers;

import controllers.annotations.*;
import controllers.constants.Error;

import models.Chat;
import models.Message;
import models.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;

public class MessagesController extends AppController {
    @Validation(name = "user_id", type = "id", require = true)
    @Validation(name = "until", type = "epoch")
    @Validation(name = "limit", type = "integer", rule = "min=1,max=25")
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

    @Validation(name = "user_id", type = "id", require = true)
    @Validation(name = "text", depend = "|attachments")
    @Validation(name = "attachments", type = "array")
    @Validation(name = "attachments[]", type = "object")
    @Validation(name = "attachments[].type", rule = "(photo|url)", require = true)
    @Validation(name = "attachments[].photo_id", type = "id", depend = "type=photo")
    @Validation(name = "attachments[].url", depend = "type=url", rule = "url")
    public static Result add(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        String text = params.has("text") ? params.get("text").textValue() : null;

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        ObjectId chatId = Chat.getChatId(me.getId(), userId);

        Message message = new Message(me.getId(), text, getAttachments(params));
        message.save(chatId);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "event");
        event.put("type", "message");
        event.putPOJO("data", message);

        sendEvent(userId, event);
        sendEvent(me.getId(), event);

        return Ok(message);
    }
}
