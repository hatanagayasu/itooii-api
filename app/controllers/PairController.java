package controllers;

import models.User;
import models.VideoChat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;

public class PairController extends AppController
{
    static
    {
    }

    private static void pair(ObjectId offerId, ObjectId answerId)
    {
        ObjectId id = new ObjectId();
        VideoChat offer = VideoChat.get(offerId);
        VideoChat answer = VideoChat.get(answerId);

        if (offer == null || answer == null || offer.getId() != null || answer.getId() != null)
            return;

        offer.pair(id, answer.getUserId(), answer.getToken());
        answer.pair(id, offer.getUserId(), offer.getToken());

        ObjectNode event = mapper.createObjectNode();
        event.put("action", "video/pair");
        event.put("video_chat_id", id.toString());

        sendEvent(offer.getId(), offer.getToken(), event);
    }
}
