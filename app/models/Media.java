package models;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Media extends Model {
    @Id
    private ObjectId id;
    private AttachmentType type;
    @JsonProperty("user_id")
    private ObjectId userId;
    private Date created;
    private Boolean posted;

    public Media() {
    }

    public Media(ObjectId userId, AttachmentType type) {
        this.id = new ObjectId();
        this.type = type;
        this.userId = userId;
        this.created = new Date();
        this.posted = false;
    }

    public void save() {
        MongoCollection mediaCol = jongo.getCollection("media");
        mediaCol.save(this);
    }

    public static void posted(List<ObjectId> ids) {
        MongoCollection mediacol = jongo.getCollection("media");
        mediacol.update("{_id:{$in:#}}", ids).multi().with("{$unset:{posted:0}}");
    }
}
