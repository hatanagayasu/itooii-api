package models;

import java.util.Date;
import java.util.Set;

import org.bson.types.ObjectId;

@lombok.Getter
public class Other extends Skim {
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
