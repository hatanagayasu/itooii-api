package controllers;

import play.Play;
import play.Configuration;

import controllers.annotations.*;
import controllers.constants.Error;

import models.User;
import models.VideoChat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.bson.types.ObjectId;

public class VideoChatController extends AppController {
    private static ArrayNode iceServers = null;

    static {
        iceServers = mapper.createArrayNode();
        Configuration conf = Play.application().configuration();
        conf.getObjectList("webrtc.iceServers").forEach(
            m -> {
                ObjectNode node = iceServers.addObject();
                m.forEach((k, v) -> node.put(k, v.toString()));
            }
        );
    }

    private static void leave(VideoChat videoChat) {
        leave(videoChat, null);
    }

    private static void leave(VideoChat videoChat, String token) {
        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/leave");

        if (token != null && !token.equals(videoChat.getToken()))
            sendEvent(videoChat.getToken(), event);

        if (videoChat.getPeerId() != null)
            sendEvent(videoChat.getPeerToken(), event);

        videoChat.leave();
    }

    public static Result ready(JsonNode params) {
        User me = getMe(params);
        String token = params.get("access_token").textValue();

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat != null)
            leave(videoChat, token);

        videoChat = new VideoChat(me.getId(), token);
        videoChat.set();

        publish("ready", me.getId() + "\n" + token + "\n" + me);

        return Ok();
    }

    public static Result leave(JsonNode params) {
        User me = getMe(params);
        String token = params.get("access_token").textValue();

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null || !token.equals(videoChat.getToken()))
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);

        leave(videoChat);

        return Ok();
    }

    @Validation(name = "user_id", type = "id", require = true)
    public static Result request(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);
        String token = params.get("access_token").textValue();

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (me.getFollowings() == null || !me.getFollowings().contains(userId) ||
            user.getFollowings() == null || !user.getFollowings().contains(me.getId()))
            return Error(Error.NOT_FRIEND);

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat != null)
            leave(videoChat, token);

        videoChat = new VideoChat(me.getId(), token);
        videoChat.set();

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/request");
        event.put("video_chat_id", videoChat.getId().toString());
        event.put("user_id", me.getId().toString());

        sendEvent(userId, event);

        return Ok();
    }

    @Validation(name = "user_id", type = "id", require = true)
    @Validation(name = "video_chat_id", type = "id", require = true)
    @Validation(name = "confirm", type = "boolean", require = true)
    public static Result response(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        ObjectId videoChatId = getObjectId(params, "video_chat_id");
        String token = params.get("access_token").textValue();

        VideoChat videoChat = VideoChat.get(userId);
        if (videoChat == null || !videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        if (params.get("confirm").booleanValue()) {
            videoChat.pair(me.getId(), token);

            videoChat = new VideoChat(me.getId(), token, userId, videoChat.getToken());
            videoChat.set();

            ObjectNode event = mapper.createObjectNode();
            event.put("action", "video/response");
            event.put("video_chat_id", videoChat.getId().toString());
            event.put("confirm", true);

            sendEvent(videoChat.getPeerToken(), event);
        } else {
            ObjectNode event = mapper.createObjectNode();
            event.put("action", "video/response");
            event.put("confirm", false);

            sendEvent(videoChat.getToken(), event);

            leave(videoChat);
        }

        return Ok();
    }

    @Validation(name = "video_chat_id", type = "id", require = true)
    public static Result pairRequest(JsonNode params) {
        User me = getMe(params);
        ObjectId videoChatId = getObjectId(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        if (!videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/pair_request");
        event.put("video_chat_id", videoChatId.toString());
        event.put("user_id", me.getId().toString());

        sendEvent(videoChat.getPeerToken(), event);

        return Ok();
    }

    @Validation(name = "video_chat_id", type = "id", require = true)
    public static Result pairResponse(JsonNode params) {
        User me = getMe(params);
        ObjectId videoChatId = getObjectId(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        ObjectId id = videoChat.getId();
        videoChat = VideoChat.get(videoChat.getPeerId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        if (!videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/pair_response");
        event.put("video_chat_id", id.toString());

        sendEvent(videoChat.getToken(), event);

        return Ok();
    }

    @Validation(name = "description", type = "object", rule = "passUnder", require = true)
    @Validation(name = "video_chat_id", type = "id", require = true)
    public static Result offer(JsonNode params) {
        User me = getMe(params);
        ObjectId videoChatId = getObjectId(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        videoChat = VideoChat.get(videoChat.getPeerId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        if (!videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/offer");
        event.set("description", params.get("description"));
        event.put("video_chat_id", videoChat.getId().toString());

        sendEvent(videoChat.getToken(), event);

        return Ok();
    }

    @Validation(name = "description", type = "object", rule = "passUnder", require = true)
    @Validation(name = "video_chat_id", type = "id", require = true)
    public static Result answer(JsonNode params) {
        User me = getMe(params);
        ObjectId videoChatId = getObjectId(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        videoChat = VideoChat.get(videoChat.getPeerId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        if (!videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/answer");
        event.set("description", params.get("description"));
        event.put("video_chat_id", videoChat.getId().toString());

        sendEvent(videoChat.getToken(), event);

        return Ok();
    }

    @Validation(name = "candidate", type = "object", rule = "passUnder", require = true)
    @Validation(name = "video_chat_id", type = "id", require = true)
    public static Result candidate(JsonNode params) {
        User me = getMe(params);
        ObjectId videoChatId = getObjectId(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        videoChat = VideoChat.get(videoChat.getPeerId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        if (!videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/candidate");
        event.set("candidate", params.get("candidate"));
        event.put("video_chat_id", videoChat.getId().toString());

        sendEvent(videoChat.getToken(), event);

        return Ok();
    }

    @Validation(name = "video_chat_id", type = "id", require = true)
    public static Result connected(JsonNode params) {
        User me = getMe(params);
        ObjectId videoChatId = getObjectId(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        videoChat = VideoChat.get(videoChat.getPeerId());
        if (videoChat == null)
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);
        if (!videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        videoChat.save();

        return Ok();
    }

    @Validation(name = "video_chat_id", type = "id", require = true)
    @Validation(name = "rate", type = "integer", rule = "min=0,max=5", require = true)
    public static Result rate(JsonNode params) {
        ObjectId videoChatId = getObjectId(params, "video_chat_id");
        int rate = params.get("rate").intValue();

        VideoChat.rate(videoChatId, rate);

        return Ok();
    }

    @Anonymous
    @Validation(name = "offer_id", type = "id", require = true)
    @Validation(name = "answer_id", type = "id", require = true)
    @Validation(name = "lang0", type = "integer", require = true)
    @Validation(name = "lang1", type = "integer", require = true)
    public static Result pair(JsonNode params) {
        VideoChat offer = VideoChat.get(getObjectId(params, "offer_id"));
        VideoChat answer = VideoChat.get(getObjectId(params, "answer_id"));

        if (offer == null || answer == null)
            return Ok();

        offer.pair(answer.getUserId(), answer.getToken());
        answer.pair(offer.getUserId(), offer.getToken());

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/pair");
        event.put("video_chat_id", offer.getId().toString());
        event.put("user_id", answer.getUserId().toString());
        event.put("lang0", params.get("lang0").intValue());
        event.put("lang1", params.get("lang1").intValue());

        sendEvent(offer.getToken(), event);

        event.removeAll();
        event.put("action", "event");
        event.put("type", "pair");
        event.put("offer_id", offer.getUserId().toString());
        event.put("answer_id", answer.getUserId().toString());

        sendEvent(event);

        return Ok();
    }

    @Anonymous
    @Validation(name = "offer_id", type = "id", require = true)
    public static Result unpair(JsonNode params) {
        VideoChat videoChat = VideoChat.get(getObjectId(params, "offer_id"));

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/unpair");

        sendEvent(videoChat.getToken(), event);

        leave(videoChat);

        return Ok();
    }

    @Anonymous
    public static Result getIceServers(JsonNode params) {
        return Ok(iceServers);
    }
}
