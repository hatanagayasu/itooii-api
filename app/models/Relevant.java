package models;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;

@lombok.Getter
public class Relevant extends Model {
    @JsonProperty("user_id")
    @Postproduct
    private ObjectId userId;
    private int type;
    private Date created;

    public Relevant() {
    }
}
