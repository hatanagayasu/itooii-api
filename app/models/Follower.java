package models;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter @lombok.Setter
public class Follower extends Model
{
    @Id
    private ObjectId id;
    @JsonProperty("user_id")
    private ObjectId userId;
    private ObjectId follower;
    private Date created;
}
