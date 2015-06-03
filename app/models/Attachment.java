package models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;

@lombok.Getter
public class Attachment extends Model {
    private AttachmentType type;
    private ObjectId id;
    private Integer width;
    private Integer height;
    private String url;
    @JsonIgnore
    @JsonProperty("user_name")
    private String userName;
    @JsonIgnore
    @JsonProperty("user_avatar")
    private ObjectId userAvatar;

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

    public static void postproduct(List<Attachment> attachments) {
        for (Attachment attachment : attachments) {
            if (attachment.type.equals(AttachmentType.follow)) {
                attachment.userName = name(attachment.id);
                attachment.userAvatar = avatar(attachment.id);
            }
        }
    }
}
