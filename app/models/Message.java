package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Message extends Model {
    @Id
    private ObjectId id;
    @JsonIgnore
    private ObjectId chatId;
    private int type; // 0:message 1:miss 2:called
    @JsonIgnore
    private String action;
    @JsonProperty("user_id")
    @Postproduct
    private ObjectId userId;
    private String text;
    private List<Attachment> attachments;
    private Date created;

    public Message() {
    }

    public Message(ObjectId userId, String text, List<Attachment> attachments) {
        this(0, userId, text, attachments);
    }

    public Message(int type, ObjectId userId, String text,
        List<Attachment> attachments) {
        this.id = new ObjectId();
        this.type = type;
        this.action = "message";
        this.userId = userId;
        this.text = text;
        this.attachments = attachments == null ? null :
            (attachments.isEmpty() ? null : attachments);
        this.created = new Date();
    }

    public void save(Chat chat) {
        MongoCollection chatCol = jongo.getCollection("chat");
        MongoCollection messageCol = jongo.getCollection("message");

        chat.getUserIds().remove(userId);

        chat = chatCol
            .findAndModify("{_id:#}", chat.getId())
            .with("{$set:{last_message:#,unread_user_ids:#},$inc:{message_count:1}}",
                this, chat.getUserIds())
            .projection("{message_count:1}")
            .as(Chat.class);

        int page = chat.getMessageCount() / 50;
        messageCol.update("{chat_id:#,page:#}", chat.getId(), page).upsert()
            .with("{$push:{messages:#},$setOnInsert:{created:#}}", this, created);

        chatId = chat.getId();
    }

    public static Page get(ObjectId myId, ObjectId chatId, long until, int limit) {
        MongoCollection messageCol = jongo.getCollection("message");
        String previous = null;

        MongoCursor<Messages> cursor = messageCol
            .find("{chat_id:#,created:{$lt:#}}", chatId, new Date(until))
            .sort("{created:-1}")
            .limit(2)
            .as(Messages.class);

        List<Messages> messageses = new ArrayList<Messages>(2);
        while (cursor.hasNext())
            messageses.add(cursor.next());

        try {
            cursor.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<Message> messages = new ArrayList<Message>(100);
        for (int i = messageses.size() - 1; i >= 0; i--) {
            for (Message message : messageses.get(i).getMessages()) {
                if (message.created.getTime() < until)
                    messages.add(message);
            }
        }

        if (messages.size() > limit)
            messages.subList(0, messages.size() - limit).clear();

        if (messages.size() == limit) {
            until = messages.get(0).getCreated().getTime();
            previous = String.format("until=%d&limit=%d", until, limit);
        }

        if (messages.size() > 0) {
            LastReadMessageId.updateLastReadMessageId(myId, chatId,
                messages.get(messages.size() - 1).getId());
        }

        return new Page(messages, previous);
    }
}
