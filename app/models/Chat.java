package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Chat extends Model {
    @Id
    private ObjectId id;
    @JsonProperty("one_on_one")
    private boolean oneOnOne = true;
    @JsonProperty("user_ids")
    private Set<ObjectId> userIds;
    private ObjectId userId;
    private String name;
    private ObjectId avatar;
    @JsonProperty("message_count")
    private int messageCount;
    private Date created;
    @JsonProperty("last_message")
    private Message lastMessage;

    public Chat() {
    }

    public static ObjectId getChatId(ObjectId userId0, ObjectId userId1) {
        String key = "chatId:" +
            (userId0.compareTo(userId1) < 0 ? userId0 + ":" + userId1 : userId1 + ":" + userId0);

        Chat chat = cache(key, Chat.class, new Callable<Chat>() {
            public Chat call() {
                MongoCollection chatCol = jongo.getCollection("chat");

                Chat chat = chatCol
                    .findAndModify("{user_ids:[#,#]}", userId0, userId1)
                    .with("{$setOnInsert:{user_ids:[#,#],created:#}}", userId0, userId1, new Date())
                    .upsert()
                    .returnNew()
                    .as(Chat.class);

                return chat;
            }
        });

        return chat.id;
    }

    public static Page list(ObjectId userId, Date until, int limit) {
        MongoCollection col = jongo.getCollection("chat");
        String previous = null;

        MongoCursor<Chat> cursor = col
            .find("{user_ids:#,'last_message.created':{$lt:#}}", userId, until)
            .sort("{'last_message.created':-1}")
            .limit(limit)
            .as(Chat.class);

        List<Chat> chats = new ArrayList<Chat>(limit);
        Chat chat = null;
        while (cursor.hasNext()) {
            chat = cursor.next();
            chats.add(chat);

            if (chat.oneOnOne) {
                chat.userIds.remove(userId);
                chat.userId = chat.userIds.iterator().next();
                chat.name = name(chat.userId);
                chat.avatar = avatar(chat.userId);
            }

            chat.userIds = null;
        }

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (chats.size() == limit) {
            previous = String.format("until=%d&limit=%d",
                chat.lastMessage.getCreated().getTime(), limit);
        }

        return new Page(chats, previous);
    }
}
