package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
    protected Date birthday;
    protected int gender;
    protected int tos;
    protected String nationality;
    protected String country;
    protected String city;
    protected String avatar;

    public Skim() {
    }

    public static Skim get(ObjectId userId) {
        return get(userId, Skim.class);
    }

    public static <T extends Skim> T get(ObjectId userId, Class<T> clazz) {
        String key = "user:" + userId;

        T t = get(key, clazz);
        if (t == null) {
            User.get(userId);
            t = get(key, clazz);
        }

        return t;
    }

    protected <T extends Skim> Page page(Set<ObjectId> ids, int skip, int limit, Class<T> clazz) {
        List<T> list = new ArrayList<T>();
        String next = null;

        if (ids != null) {
            Iterator<ObjectId> iterator = ids.iterator();

            int count = 0;
            while (count < skip && iterator.hasNext()) {
                iterator.next();
                count++;
            }

            count = 0;
            while (count < limit && iterator.hasNext()) {
                T t = T.get(iterator.next(), clazz);
                if (t != null)
                   list.add(t);
                count++;
            }

            if (iterator.hasNext())
                next = String.format("skip=%d&limit=%d", skip + limit, limit);
        }

        return new Page(list, null, next);
    }
}
