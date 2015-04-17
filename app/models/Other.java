package models;

import java.util.Date;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Other extends Model {
    @Id
    protected ObjectId id;
    protected String name;
    @JsonProperty("native_language")
    protected List<Integer> nativeLanguage;
    @JsonProperty("practice_language")
    protected List<PracticeLanguage> practiceLanguage;
    protected Date created;
    protected Set<ObjectId> followings;
    protected Set<ObjectId> followers;

    public Other() {
    }

    public static Other get(ObjectId userId) {
        String key = "user:" + userId;

        Other other = get(key, Other.class);
        if (other == null) {
            User.get(userId);
            other = get(key, Other.class);
        }

        return other;
    }
}
