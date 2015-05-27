package models;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;

@lombok.Getter
public class Relevant extends Model {
    @JsonProperty("user_id")
    private ObjectId userId;
    private int type;
    private Date created;
    @JsonIgnore
    @JsonProperty("user_name")
    private String userName;
    @JsonIgnore
    @JsonProperty("user_avatar")
    private ObjectId userAvatar;

    public Relevant() {
    }

    public void postproduct() {
        userName = name(this.userId);
        userAvatar = avatar(this.userId);
    }
}
