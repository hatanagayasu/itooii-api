package controllers;

import controllers.annotations.*;
import controllers.constants.Error;
import controllers.pair.PairedTalkData;

import models.User;
import models.VideoChat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;

public class VideoChatController extends AppController
{
    private static void leave(VideoChat videoChat)
    {
        leave(videoChat, null);
    }

    private static void leave(VideoChat videoChat, String token)
    {
        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/leave");

        if (token != null && !token.equals(videoChat.getToken()))
            sendEvent(videoChat.getUserId(), videoChat.getToken(), event);

        if (videoChat.getPeerId() != null)
            sendEvent(videoChat.getPeerId(), videoChat.getPeerToken(), event);

        videoChat.leave();
    }

    public static Result ready(JsonNode params)
    {
        User me = getMe(params);
        String token = params.get("access_token").textValue();

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat != null)
            leave(videoChat, token);

        videoChat = new VideoChat(me.getId(), token);
        videoChat.set();

        inPairQueue(me.getId());

        return Ok();
    }

    public static Result leave(JsonNode params)
    {
        User me = getMe(params);
        String token = params.get("access_token").textValue();

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null || !token.equals(videoChat.getToken()))
            return Error(Error.INVALID_VIDEO_ACCESS_TOKEN);

        leave(videoChat);

        return Ok();
    }

    @Validation(name="user_id", type="id", require=true)
    public static Result request(JsonNode params)
    {
        User me = getMe(params);
        ObjectId userId = getObject(params, "user_id");
        User user = User.getById(userId);
        String token = params.get("access_token").textValue();

        if (!me.getFollowings().contains(userId) || !user.getFollowings().contains(me.getId()))
            return Error(Error.NOT_FRIEND);

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat != null)
            leave(videoChat, token);

        ObjectId videoChatId = new ObjectId();
        videoChat = new VideoChat(videoChatId, me.getId(), token);
        videoChat.set();

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/request");
        event.put("user_id", me.getId().toString());
        event.put("video_chat_id", videoChatId.toString());

        sendEvent(userId, event);

        return Ok();
    }

    @Validation(name="user_id", type="id", require=true)
    @Validation(name="video_chat_id", type="id", require=true)
    @Validation(name="confirm", type="boolean", require=true)
    public static Result response(JsonNode params)
    {
        User me = getMe(params);
        ObjectId userId = getObject(params, "user_id");
        ObjectId videoChatId = getObject(params, "video_chat_id");
        String token = params.get("access_token").textValue();

        VideoChat videoChat = VideoChat.get(userId);
        if (videoChat == null || !videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        if (params.get("confirm").booleanValue())
        {
            videoChat.pair(me.getId(), token);

            videoChat = new VideoChat(videoChatId, me.getId(), token, userId, videoChat.getToken());
            videoChat.set();

            ObjectNode event = mapper.createObjectNode();
            event.put("action", "video/response");
            event.put("video_chat_id", videoChatId.toString());
            event.put("confirm", true);

            sendEvent(videoChat.getPeerId(), videoChat.getPeerToken(), event);
        }
        else
        {
            ObjectNode event = mapper.createObjectNode();
            event.put("action", "video/response");
            event.put("video_chat_id", videoChatId.toString());
            event.put("confirm", false);

            sendEvent(videoChat.getUserId(), videoChat.getToken(), event);

            leave(videoChat);
        }

        return Ok();
    }

    @Validation(name="video_chat_id", type="id", require=true)
    public static Result pairRequest(JsonNode params)
    {
        User me = getMe(params);
        ObjectId videoChatId = getObject(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null || !videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/pair_request");
        event.put("video_chat_id", videoChatId.toString());

        sendEvent(videoChat.getPeerId(), videoChat.getPeerToken(), event);

        return Ok();
    }

    @Validation(name="video_chat_id", type="id", require=true)
    public static Result pairResponse(JsonNode params)
    {
        User me = getMe(params);
        ObjectId videoChatId = getObject(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null || !videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/pair_response");
        event.put("video_chat_id", videoChatId.toString());

        sendEvent(videoChat.getPeerId(), videoChat.getPeerToken(), event);

        return Ok();
    }

    @Validation(name="description", type="object", rule="passUnder", require=true)
    @Validation(name="video_chat_id", type="id", require=true)
    public static Result offer(JsonNode params)
    {
        User me = getMe(params);
        ObjectId videoChatId = getObject(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null || !videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/offer");
        event.put("description", params.get("description"));
        event.put("video_chat_id", videoChatId.toString());

        sendEvent(videoChat.getPeerId(), videoChat.getPeerToken(), event);

        return Ok();
    }

    @Validation(name="description", type="object", rule="passUnder", require=true)
    @Validation(name="video_chat_id", type="id", require=true)
    public static Result answer(JsonNode params)
    {
        User me = getMe(params);
        ObjectId videoChatId = getObject(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null || !videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/answer");
        event.put("description", params.get("description"));
        event.put("video_chat_id", videoChatId.toString());

        sendEvent(videoChat.getPeerId(), videoChat.getPeerToken(), event);

        return Ok();
    }

    @Validation(name="candidate", type="object", rule="passUnder", require=true)
    @Validation(name="video_chat_id", type="id", require=true)
    public static Result candidate(JsonNode params)
    {
        User me = getMe(params);
        ObjectId videoChatId = getObject(params, "video_chat_id");

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat == null || !videoChatId.equals(videoChat.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/candidate");
        event.put("candidate", params.get("candidate"));
        event.put("video_chat_id", videoChatId.toString());

        sendEvent(videoChat.getPeerId(), videoChat.getPeerToken(), event);

        return Ok();
    }

    public static void pair(PairedTalkData pairedData)
    {
        ObjectId id = new ObjectId();
        VideoChat offer = VideoChat.get(pairedData.getOfferId());
        VideoChat answer = VideoChat.get(pairedData.getAnswerId());

        if (offer == null || answer == null || offer.getId() != null || answer.getId() != null)
            return;

        offer.pair(id, answer.getUserId(), answer.getToken());
        answer.pair(id, offer.getUserId(), offer.getToken());

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/pair");
        event.put("video_chat_id", id.toString());
        event.put("lang0", pairedData.getLang0());
        event.put("lang1", pairedData.getLang1());

        sendEvent(offer.getId(), offer.getToken(), event);
    }
}
