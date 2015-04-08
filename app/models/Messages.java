package models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Messages extends Model {
    @Id
    private ObjectId id;
    @JsonProperty("chat_id")
    private ObjectId chatId;
    private int page;
    private List<Message> messages;

    public Messages() {
    }
}
