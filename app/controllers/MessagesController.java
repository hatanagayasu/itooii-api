package controllers;

import controllers.constants.Error;

import models.Chat;
import models.Message;
import models.User;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;

public class MessagesController extends AppController {
    public static Result getChatId(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        User user = User.get(userId);
        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (user.getBlockings() != null && user.getBlockings().contains(me.getId()))
            return Error(Error.OBJECT_FORBIDDEN);

        Chat chat = Chat.get(me.getId(), userId);

        ObjectNode result = mapper.createObjectNode();
        result.put("chat_id", chat.getId().toString());

        return Ok(result);
    }

    public static Result get(JsonNode params) {
        User me = getMe(params);
        ObjectId chatId = getObjectId(params, "chat_id");
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        Chat chat = Chat.get(chatId);
        if (chat == null)
            return Error(Error.CHAT_NOT_FOUND);

        if (!chat.getUserIds().contains(me.getId()))
            return Error(Error.OBJECT_FORBIDDEN);

        return Ok(Message.get(me.getId(), chatId, until, limit));
    }

    public static Result add(JsonNode params) {
        User me = getMe(params);
        ObjectId chatId = getObjectId(params, "chat_id");
        String text = params.has("text") ? params.get("text").textValue() : null;

        Chat chat = Chat.get(chatId);
        if (chat == null)
            return Error(Error.CHAT_NOT_FOUND);

        if (!chat.getUserIds().contains(me.getId()))
            return Error(Error.OBJECT_FORBIDDEN);

        Message message = new Message(me.getId(), text, getAttachments(params));
        message.save(chat);

        chat.getUserIds().forEach(userId -> sendEvent(userId, message));

        return Ok(message);
    }

    public static Result list(JsonNode params) {
        User me = getMe(params);
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;
        boolean reset = params.has("reset") ? params.get("reset").booleanValue() : false;

        return Ok(Chat.list(me.getId(), new Date(until), limit, reset));
    }
}
