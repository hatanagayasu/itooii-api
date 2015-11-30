package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class VideoChat extends Model {
    @Id
    private ObjectId id;
    private VideoChatType type;
    @JsonProperty("user_id")
    private ObjectId userId;
    private String token;
    @JsonProperty("peer_id")
    @Postproduct("peer")
    private ObjectId peerId;
    private String peerToken;
    private Integer lang0, lang1;
    @JsonProperty("event_id")
    private ObjectId eventId;
    private Date created;
    private Integer rate;

    public VideoChat() {
    }

    public VideoChat(VideoChatType type, ObjectId userId, String token, ObjectId eventId) {
        this.id = new ObjectId();
        this.type = type;
        this.userId = userId;
        this.token = token;
        this.eventId = eventId;
    }

    public VideoChat(VideoChatType type, ObjectId userId, String token,
        ObjectId peerId, String peerToken, ObjectId eventId) {
        this.id = new ObjectId();
        this.type = type;
        this.userId = userId;
        this.token = token;
        this.peerId = peerId;
        this.peerToken = peerToken;
        this.eventId = eventId;
    }

    public static VideoChat get(ObjectId userId) {
        return get("video:chat:" + userId, VideoChat.class);
    }

    public void set() {
        set("video:chat:" + userId);
    }

    public void leave() {
        del("video:chat:" + userId);
        if (peerId != null) {
            del("video:chat:" + peerId);
            if (eventId != null) {
                srem("event:talking:" + eventId, userId.toString(), peerId.toString());
                Set<String> sessions = smembers("event:token:" + eventId);
                if (sessions != null && sessions.size() > 0) {
                    ObjectNode result = mapper.createObjectNode();
                    result.put("action", "event/talking")
                        .put("user_id", userId.toString())
                        .put("event_id", eventId.toString())
                        .put("talking", false);

                    publish("session", sessions + "\n" + result);

                    result.put("user_id", peerId.toString());

                    publish("session", sessions + "\n" + result);
                }
            }
        }
    }

    public void pair(ObjectId userId, String token) {
        this.peerId = userId;
        this.peerToken = token;
        set();
    }

    public void pair(ObjectId userId, String token, int lang0, int lang1) {
        this.lang0 = lang0;
        this.lang1 = lang1;
        pair(userId, token);
    }

    public void save() {
        MongoCollection videoChatCol = jongo.getCollection("videochat");

        this.token = null;
        this.peerToken = null;
        this.created = new Date();

        videoChatCol.save(this);

        if (eventId != null) {
            sadd("event:talking:" + eventId, userId.toString());
            Set<String> sessions = smembers("event:token:" + eventId);
            if (sessions != null && sessions.size() > 0) {
                ObjectNode result = mapper.createObjectNode();
                result.put("action", "event/talking")
                    .put("user_id", userId.toString())
                    .put("event_id", eventId.toString())
                    .put("talking", true);

                publish("session", sessions + "\n" + result);
            }
        }
    }

    public Map<ObjectId,Long> recentPeers() {
        MongoCollection videoChatCol = jongo.getCollection("videochat");

        MongoCursor<VideoChat> cursor = videoChatCol
            .find("{user_id:#}", userId)
            .projection("{peer_id:1,created:1}")
            .sort("{created:-1}")
            .limit(10)
            .as(VideoChat.class);

        Map<ObjectId,Long> peers = new HashMap<ObjectId,Long>();
        while (cursor.hasNext()) {
            VideoChat videoChat = cursor.next();
            if (!peers.containsKey(videoChat.getPeerId()))
                peers.put(videoChat.getPeerId(), videoChat.getCreated().getTime());
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return peers;
    }

    public static void rate(ObjectId userId, ObjectId id, int rate) {
        MongoCollection videoChatCol = jongo.getCollection("videochat");

        videoChatCol.update("{_id:#,user_id:#}", id, userId).with("{$set:{rate:#}}", rate);
    }

    public static Page getHistory(ObjectId userId, VideoChatType type, Date until, int limit) {
        MongoCollection col = jongo.getCollection("videochat");
        String previous = null;

        Find find = type == null ? col.find("{user_id:#,created:{$lt:#}}", userId, until) :
            col.find("{user_id:#,type:#,created:{$lt:#}}", userId, type, until);

        MongoCursor<VideoChat> cursor = find
            .sort("{created:-1}")
            .limit(limit)
            .as(VideoChat.class);

        List<VideoChat> videoChats = new ArrayList<VideoChat>(limit);
        VideoChat videoChat = null;
        while (cursor.hasNext()) {
            videoChat = cursor.next();
            videoChats.add(videoChat);
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (videoChats.size() == limit)
            previous = String.format("until=%d&limit=%d", videoChat.created.getTime(), limit);

        return new Page(videoChats, previous);
    }
}
