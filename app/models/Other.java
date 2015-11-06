package models;

import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.types.ObjectId;

@lombok.Getter
public class Other extends Skim {
    protected Date created;
    protected Set<ObjectId> followings;
    protected Set<ObjectId> followers;
    @JsonDeserialize(using=CustomJsonDeserializer.class)
    protected JsonNode metadata;

    public Other() {
    }

    public static Other get(ObjectId userId) {
        return get(userId, Other.class);
    }
}

