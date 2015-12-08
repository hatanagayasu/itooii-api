package controllers;

import play.Play;
import play.Configuration;

import controllers.constants.Error;

import models.Event;
import models.Pair;
import models.PracticeLanguage;
import models.User;
import models.VideoChat;
import models.VideoChatType;

import java.util.Date;
import java.util.Iterator;

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

    public static Result getPaired(JsonNode params) {
        ObjectId eventId = params.has("event_id") ? getObjectId(params, "event_id") : null;
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(Pair.get(eventId, new Date(until), limit));
    }

    public static Result getHistory(JsonNode params) {
        User me = getMe(params);
        VideoChatType type = params.has("type") ?
            VideoChatType.valueOf(params.get("type").textValue()) : null;
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(VideoChat.getHistory(me.getId(), type, new Date(until), limit));
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
        ObjectId eventId = getObjectId(params, "event_id");
        ObjectId myId = me.getId();

        VideoChat videoChat = VideoChat.get(myId);
        if (videoChat != null)
            leave(videoChat, token);

        if (eventId != null) {
            Event event = Event.get(eventId);
            if (event == null || event.getDeleted() != null)
                return NotFound();

//            if (!event.getMembers().contains(myId))
//                return ObjectForbidden();

            Iterator<Integer> nativeLanguages = me.getNativeLanguage().iterator();
            while (nativeLanguages.hasNext()) {
                int lang = nativeLanguages.next();
                if (lang != event.getLang0() && (event.getLang1() == 0 || lang != event.getLang1()))
                    nativeLanguages.remove();
            }

            Iterator<PracticeLanguage>practiceLanguages = me.getPracticeLanguage().iterator();
            while (practiceLanguages.hasNext()) {
                PracticeLanguage practiceLanguage = practiceLanguages.next();
                int lang = practiceLanguage.getId();
                if (lang != event.getLang0() && (event.getLang1() == 0 || lang != event.getLang1()))
                    practiceLanguages.remove();
            }
        }

        videoChat = new VideoChat(VideoChatType.pair, myId, token, eventId);
        videoChat.set();

        publish("ready", myId + "\n" + token + "\n" + me + "\n" + eventId + "\n" +
            videoChat.recentPeers());

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

    public static Result request(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        ObjectId eventId = getObjectId(params, "event_id");
        User user = User.get(userId);
        String token = params.get("access_token").textValue();

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (eventId != null) {
            Event event = Event.get(eventId);
            if (event == null)
                return Error(Error.EVENT_NOT_FOUND);
            if (!event.isOnline(me.getId()) || !event.isOnline(userId))
                return Error(Error.OBJECT_FORBIDDEN);
        } else {
            if (me.getFollowings() == null || !me.getFollowings().contains(userId) ||
                user.getFollowings() == null || !user.getFollowings().contains(me.getId()))
                return Error(Error.NOT_FRIEND);
        }

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat != null)
            leave(videoChat, token);

        videoChat = new VideoChat(VideoChatType.request, me.getId(), token, eventId);
        videoChat.set();

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/request");
        event.put("video_chat_id", videoChat.getId().toString());
        event.put("user_id", me.getId().toString());

        sendEvent(userId, event);

        return Ok();
    }

    public static Result cancel(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        ObjectId eventId = getObjectId(params, "event_id");
        User user = User.get(userId);
        String token = params.get("access_token").textValue();

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (eventId != null) {
            Event event = Event.get(eventId);
            if (event == null)
                return Error(Error.EVENT_NOT_FOUND);
        } else {
            if (me.getFollowings() == null || !me.getFollowings().contains(userId) ||
                user.getFollowings() == null || !user.getFollowings().contains(me.getId()))
                return Error(Error.NOT_FRIEND);
        }

        VideoChat videoChat = VideoChat.get(me.getId());
        if (videoChat != null)
            leave(videoChat, token);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/cancel");
        event.put("user_id", me.getId().toString());

        sendEvent(userId, event);

        return Ok();
    }

    public static Result response(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        ObjectId videoChatId = getObjectId(params, "video_chat_id");
        String token = params.get("access_token").textValue();

        VideoChat offer = VideoChat.get(userId);
        if (offer == null || !videoChatId.equals(offer.getId()))
            return Error(Error.INVALID_VIDEO_CHAT_ID);

        if (params.get("confirm").booleanValue()) {
            VideoChat answer = VideoChat.get(me.getId());
            if (answer != null)
                leave(answer, token);

            offer.pair(me.getId(), token);

            answer = new VideoChat(VideoChatType.request, me.getId(), token,
                userId, offer.getToken(), offer.getEventId());
            answer.set();

            ObjectNode event = mapper.createObjectNode();
            event.put("action", "video/response");
            event.put("video_chat_id", answer.getId().toString());
            event.put("confirm", true);

            sendEvent(offer.getToken(), event);
        } else {
            ObjectNode event = mapper.createObjectNode();
            event.put("action", "video/response");
            event.put("confirm", false);

            sendEvent(offer.getToken(), event);

            leave(offer);
        }

        return Ok();
    }

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
        event.put("lang0", videoChat.getLang0());
        event.put("lang1", videoChat.getLang1());

        sendEvent(videoChat.getPeerToken(), event);

        return Ok();
    }

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

    public static Result rate(JsonNode params) {
        User me = getMe(params);
        ObjectId videoChatId = getObjectId(params, "video_chat_id");
        int rate = params.get("rate").intValue();

        VideoChat.rate(me.getId(), videoChatId, rate);

        return Ok();
    }

    public static Result pair(JsonNode params) {
        VideoChat offer = VideoChat.get(getObjectId(params, "offer_id"));
        VideoChat answer = VideoChat.get(getObjectId(params, "answer_id"));

        if (offer == null || answer == null)
            return Ok();

        int lang0 = params.get("lang0").intValue();
        int lang1 = params.get("lang1").intValue();

        offer.pair(answer.getUserId(), answer.getToken(), lang0, lang1);
        answer.pair(offer.getUserId(), offer.getToken(), lang0, lang1);

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/pair");
        event.put("video_chat_id", offer.getId().toString());
        event.put("user_id", answer.getUserId().toString());
        event.put("lang0", lang0);
        event.put("lang1", lang1);

        sendEvent(offer.getToken(), event);

        Pair pair = new Pair(offer.getUserId(), answer.getUserId(), lang0, lang1,
            offer.getEventId());
        pair.save();

        sendEvent(pair);

        return Ok();
    }

    public static Result unpair(JsonNode params) {
        VideoChat videoChat = VideoChat.get(getObjectId(params, "offer_id"));

        if (videoChat != null) {
            ObjectNode event = mapper.createObjectNode();
            event.put("action", "video/unpair");

            sendEvent(videoChat.getToken(), event);

            leave(videoChat);
        }

        return Ok();
    }

    public static Result getIceServers(JsonNode params) {
        return Ok(iceServers);
    }
}
