package models;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.types.ObjectId;

@lombok.Getter
public class Other extends Skim {
    protected Date created;
    protected Set<ObjectId> friends;
    protected Set<ObjectId> followings;
    protected Set<ObjectId> followers;
    protected Set<ObjectId> unfollowers;
    protected Set<ObjectId> mutualFriends;
    @JsonIgnore
    protected boolean followed;
    @JsonIgnore
    @JsonProperty("friend_request")
    protected boolean friendRequest;
    @JsonDeserialize(using=CustomJsonDeserializer.class)
    protected JsonNode metadata;

    public Other() {
    }

    public static Other get(ObjectId userId, User user) {
        Other other = get(userId, Other.class);

        if (other == null)
            return null;

        if (user != null)
            other.postproduct(user);

        other.unfollowers = null;

        return other;
    }

    private void postproduct(User user) {
        ObjectId userId = user.getId();

        if (followers.contains(id) ||
            (friends.contains(id) && (unfollowers == null || !unfollowers.contains(id))))
            followed = true;

        friendRequest = friends.contains(id) ?
            true : user.friendRequest(userId);

        mutualFriends = new HashSet<ObjectId>(friends);
        mutualFriends.retainAll(user.friends);
    }

    public Page getMutualFriend(int skip, int limit) {
        return page(mutualFriends, skip, limit, Skim.class);
    }
}

