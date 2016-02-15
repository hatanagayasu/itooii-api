package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    @JsonProperty("user_id")
    private ObjectId userId;
    private String name;
    private ObjectId avatar;
    @JsonProperty("message_count")
    private int messageCount;
    private Date created;
    @JsonProperty("unread_user_ids")
    private Set<ObjectId> unreadUserIds;
    @JsonProperty("last_message")
    private Message lastMessage;
    @JsonIgnore
    private boolean read;

    public Chat() {
    }

    public static Chat get(ObjectId userId0, ObjectId userId1) {
        return userId0.compareTo(userId1) < 0 ?
            _get(userId0, userId1) : _get(userId1, userId0);
    }

    private static Chat _get(ObjectId userId0, ObjectId userId1) {
        String key = "chat:" + userId0 + ":" + userId1;

        Chat chat = cache(key, Chat.class, new Callable<Chat>() {
            public Chat call() {
                MongoCollection chatCol = jongo.getCollection("chat");

                Chat chat = chatCol
                    .findAndModify("{user_ids:[#,#]}", userId0, userId1)
                    .with("{$setOnInsert:{user_ids:[#,#],created:#}}", userId0, userId1, new Date())
                    .projection("{unread_user_ids:0,last_message:0}")
                    .upsert()
                    .returnNew()
                    .as(Chat.class);

                return chat;
            }
        });

        return chat;
    }

    public static Page list(ObjectId userId, Date until, int limit, boolean reset) {
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
            chat.read = chat.lastMessage.getId().compareTo(
                LastReadMessageId.getLastReadMessageId(userId, chat.getId())) == 0;
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

        if (reset) {
            col.update("{unread_user_ids:#}", userId)
                .with("{$pull:{unread_user_ids:#}}", userId);

            ObjectNode event = mapper.createObjectNode();
            event.put("action", "badge/update")
                .putArray("chats");

            publish("user", userId + "\n" + event);
        }

        return new Page(chats, previous);
    }

    public static Set<ObjectId> getUnreadChatIds(ObjectId userId) {
        MongoCollection col = jongo.getCollection("chat");

        MongoCursor<Chat> cursor = col
            .find("{unread_user_ids:#}", userId)
            .projection("{_id:1}")
            .as(Chat.class);

        Set<ObjectId> ids = new HashSet<ObjectId>();
        while (cursor.hasNext())
            ids.add(cursor.next().getId());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return ids;
    }
}
