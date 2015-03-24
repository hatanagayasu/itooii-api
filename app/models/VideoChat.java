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

    private static VideoChat get(Jedis jedis, ObjectId userId) {
        byte[] key = new String("video:chat:" + userId.toString()).getBytes();
        byte[] bytes = jedis.get(key);

        if (bytes == null)
            return null;

        return (VideoChat) unserialize(bytes);
    }

    public static VideoChat get(ObjectId userId) {
        Jedis jedis = getJedis();
        VideoChat videoChat = get(jedis, userId);
        returnJedis(jedis);

        return videoChat;
    }

    private void set(Jedis jedis) {
        byte[] key = new String("video:chat:" + userId.toString()).getBytes();
        jedis.setex(key, 86400, serialize(this));
    }

    public void set() {
        Jedis jedis = getJedis();
        set(jedis);
        returnJedis(jedis);
    }

    private void leave(Jedis jedis) {
        byte[] key = new String("video:chat:" + userId.toString()).getBytes();
        jedis.del(key);

        if (peerId != null) {
            key = new String("video:chat:" + peerId.toString()).getBytes();
            jedis.del(key);
        }
    }

    public void leave() {
        Jedis jedis = getJedis();
        leave(jedis);
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
