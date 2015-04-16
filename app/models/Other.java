package models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Other extends Model {
    @Id
    private ObjectId id;
    private String name;
    @JsonProperty("native_language")
    private List<Integer> nativeLanguage;
    @JsonProperty("practice_language")
    private List<PracticeLanguage> practiceLanguage;

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
