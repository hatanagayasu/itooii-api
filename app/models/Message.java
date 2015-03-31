package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Message extends Model {
    private static final long serialVersionUID = -1;

    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    @JsonIgnore
    @JsonProperty("user_name")
    private String userName;
    private String text;
    private List<Attachment> attachments;
    private Date created;

    public Message() {
    }

    public Message(ObjectId userId, String text, List<Attachment> attachments) {
        this.id = new ObjectId();
        this.userId = userId;
        this.text = text;
        this.attachments = attachments == null ? null :
            (attachments.isEmpty() ? null : attachments);
        this.created = new Date();
    }

    public void save(ObjectId chatId) {
        MongoCollection chatCol = jongo.getCollection("chat");
        MongoCollection messageCol = jongo.getCollection("message");

        Chat chat = chatCol
            .findAndModify("{_id:#}", chatId)
            .with("{$inc:{message_count:1}}")
            .projection("{message_count:1}")
            .as(Chat.class);

        if (chat == null)
            return;

        int page = chat.getMessageCount() / 50;
        messageCol.update("{chat_id:#,page:#}", chatId, page).upsert()
            .with("{$push:{messages:#},$setOnInsert:{created:#}}", this, created);
    }

    public static Page get(ObjectId chatId, long until, int limit) {
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

        for (Message message : messages)
            message.userName = name(message.userId);

        if (messages.size() == limit) {
            until = messages.get(0).getCreated().getTime();
            previous = String.format("until=%d&limit=%d", until, limit);
        }

        return new Page(messages, previous);
    }
}
