package models;

import org.bson.types.ObjectId;
import redis.clients.jedis.Jedis;

@lombok.Getter
public class VideoChat extends Model {
    private ObjectId id;
    private ObjectId userId;
    private String token;
    private ObjectId peerId;
    private String peerToken;

    public VideoChat() {
    }

    public VideoChat(ObjectId userId, String token) {
        this.userId = userId;
        this.token = token;
    }

    public VideoChat(ObjectId id, ObjectId userId, String token) {
        this.id = id;
        this.userId = userId;
        this.token = token;
    }

    public VideoChat(ObjectId id, ObjectId userId, String token, ObjectId peerId, String peerToken) {
        this.id = id;
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

    public void pair(ObjectId id, ObjectId userId, String token) {
        this.id = id;
        this.peerId = userId;
        this.peerToken = token;
        set();
    }
}
