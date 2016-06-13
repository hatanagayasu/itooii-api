package models;

import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class LastReadMessageId extends Model {
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    @JsonProperty("chat_id")
    private ObjectId chatId;
    @JsonProperty("message_id")
    private ObjectId messageId;

    public LastReadMessageId() {
    }

    public LastReadMessageId(ObjectId userId, ObjectId chatId, ObjectId messageId) {
        this.id = new ObjectId();
        this.userId = userId;
        this.chatId = chatId;
        this.messageId = messageId;
    }

    public static ObjectId getLastReadMessageId(ObjectId userId, ObjectId chatId) {
        MongoCollection col = jongo.getCollection("last_read_message_id");
        String key = "last_read_message_id:" + userId + ":" + chatId;

        LastReadMessageId lastReadMessageId = cache(key, LastReadMessageId.class,
            new Callable<LastReadMessageId>() {
            public LastReadMessageId call() {
                return col.findOne("{user_id:#,chat_id:#}", userId, chatId)
                    .as(LastReadMessageId.class);
            }
        });

        return lastReadMessageId == null ? epochId : lastReadMessageId.getMessageId();
    }

    public static void updateLastReadMessageId(ObjectId userId, ObjectId chatId,
        ObjectId messageId) {
        ObjectId id = getLastReadMessageId(userId, chatId);

        if (messageId.compareTo(id) > 0) {
            MongoCollection col = jongo.getCollection("last_read_message_id");
            String key = "last_read_message_id:" + userId + ":" + chatId;

            col.update("{user_id:#,chat_id:#}", userId, chatId)
                .upsert()
                .with("{$set:{message_id:#}}", messageId);

            del(key);

            MongoCollection chatCol = jongo.getCollection("chat");
            chatCol.update("{_id:#}", chatId)
                .with("{$pull:{unread_user_ids:#}}", userId);
        }
    }
}
