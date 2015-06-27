package models;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class VideoChat extends Model {
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    private String token;
    @JsonProperty("peer_id")
    private ObjectId peerId;
    private String peerToken;
    private int lang0, lang1;
    @JsonProperty("event_id")
    private ObjectId eventId;
    private Date created;
    private int rate;

    public VideoChat() {
    }

    public VideoChat(ObjectId userId, String token) {
        this.id = new ObjectId();
        this.userId = userId;
        this.token = token;
    }

    public VideoChat(ObjectId userId, String token, ObjectId peerId, String peerToken) {
        this.id = new ObjectId();
        this.userId = userId;
        this.token = token;
        this.peerId = peerId;
        this.peerToken = peerToken;
    }

    public static VideoChat get(ObjectId userId) {
        return get("video:chat:" + userId, VideoChat.class);
    }

    public void set() {
        set("video:chat:" + userId);
    }

    public void leave() {
        del("video:chat:" + userId);
        if (peerId != null)
            del("video:chat:" + peerId);
    }

    public void pair(ObjectId userId, String token) {
        this.peerId = userId;
        this.peerToken = token;
        set();
    }

    public void pair(ObjectId userId, String token, int lang0, int lang1, ObjectId eventId) {
        this.lang0 = lang0;
        this.lang1 = lang1;
        this.eventId = eventId;
        pair(userId, token);
    }

    public void save() {
        MongoCollection videoChatCol = jongo.getCollection("videochat");

        this.token = null;
        this.peerToken = null;
        this.created = new Date();

        videoChatCol.save(this);
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
}
