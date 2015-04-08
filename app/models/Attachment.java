package models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;

@lombok.Getter
public class Attachment extends Model {
    private AttachmentType type;
    @JsonProperty("photo_id")
    private ObjectId photoId;
    private String url;
    private String preview;

    public Attachment() {
    }

    public Attachment(String type, ObjectId photoId) {
        this.type = AttachmentType.valueOf(type);
        this.photoId = photoId;
    }

    public Attachment(String type, String url, String preview) {
        this.type = AttachmentType.valueOf(type);
        this.url = url;
        this.preview = preview;
    }
}
