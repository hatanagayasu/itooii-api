package models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Comments extends Model {
    private static final long serialVersionUID = -1;

    @Id
    private ObjectId id;
    @JsonProperty("post_id")
    private ObjectId postId;
    private int page;
    private int count;
    private List<Comment> comments;

    public Comments() {
    }
}
