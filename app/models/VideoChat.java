package models;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;
import redis.clients.jedis.Jedis;

@lombok.Getter
public class VideoChat extends Model {
    @Id
    private ObjectId id;
    private ObjectId userId;
    private String token;
    private ObjectId peerId;
    private String peerToken;
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
        Jedis jedis = getJedis();
        VideoChat videoChat = get(jedis, "video:chat:" + userId, VideoChat.class);
        returnJedis(jedis);

        return videoChat;
    }

    public void set() {
        Jedis jedis = getJedis();
        set(jedis, "video:chat:" + userId);
        returnJedis(jedis);
    }

    public void leave() {
        Jedis jedis = getJedis();
        jedis.del("video:chat:" + userId);
        if (peerId != null)
            jedis.del("video:chat:" + peerId);
        returnJedis(jedis);
    }

    public void pair(ObjectId userId, String token) {
        this.peerId = userId;
        this.peerToken = token;
        set();
    }

    public void save() {
        MongoCollection videoChatCol = jongo.getCollection("videochat");

        this.token = null;
        this.peerToken = null;
        this.created = new Date();

        videoChatCol.save(this);
    }

    public static void rate(ObjectId id, int rate) {
        MongoCollection videoChatCol = jongo.getCollection("videochat");

        videoChatCol.update(id).with("{$set:{rate:#}}", rate);
    }
}
