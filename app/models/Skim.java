package models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.jongo.marshall.jackson.oid.Id;

@lombok.Getter
public class Skim extends Model {
    @Id
    protected ObjectId id;
    protected String name;
    @JsonProperty("native_language")
    protected List<Integer> nativeLanguage;
    @JsonProperty("practice_language")
    protected List<PracticeLanguage> practiceLanguage;

    public Skim() {
    }

    public static Skim get(ObjectId userId) {
        String key = "user:" + userId;

        Skim skim = get(key, Skim.class);
        if (skim == null) {
            User.get(userId);
            skim = get(key, Skim.class);
        }

        return skim;
    }
}
