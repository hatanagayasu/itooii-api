package models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;

@lombok.Getter
public class Attachment extends Model {
    private static final long serialVersionUID = -1;

    private AttachmentType type;
    @JsonProperty("photo_id")
    private ObjectId photoId;
    private String preview;

    public Attachment() {
    }

    public Attachment(String type, ObjectId photoId) {
        this.type = AttachmentType.valueOf(type);
        this.photoId = photoId;
    }

    public Attachment(String type, String preview) {
        this.type = AttachmentType.valueOf(type);
        this.preview = preview;
    }
}
