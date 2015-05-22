package models;

import org.bson.types.ObjectId;

@lombok.Getter
public class Attachment extends Model {
    private AttachmentType type;
    private ObjectId id;
    private Integer width;
    private Integer height;
    private String url;

    public Attachment() {
    }

    public Attachment(AttachmentType type, ObjectId id) {
        this.type = type;
        this.id = id;
    }

    public Attachment(AttachmentType type, ObjectId id, int width, int height) {
        this.type = type;
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public Attachment(AttachmentType type, String url) {
        this.type = type;
        this.url = url;
    }
}
