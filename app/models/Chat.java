package models;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Chat extends Model {
    @Id
    private ObjectId id;
    @JsonProperty("user_ids")
    private Set<ObjectId> userId;
    @JsonProperty("message_count")
    private int messageCount;
    private Date created;

    public Chat() {
    }

    public static ObjectId getChatId(ObjectId userId0, ObjectId userId1) {
        String key = "chatId:" +
            (userId0.compareTo(userId1) < 0 ? userId0 + ":" + userId1 : userId1 + ":" + userId0);

        Chat chat = cache(key, Chat.class, new Callable<Chat>() {
            public Chat call() {
                MongoCollection chatCol = jongo.getCollection("chat");

                Chat chat = chatCol
                    .findAndModify("{user_ids:{$in:[#,#],$size:2}}", userId0, userId1)
                    .with("{$setOnInsert:{user_ids:[#,#],created:#}}", userId0, userId1, new Date())
                    .upsert()
                    .returnNew()
                    .as(Chat.class);

                return chat;
            }
        });

        return chat.id;
    }
}
