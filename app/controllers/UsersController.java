package controllers;

import controllers.constants.Error;

import models.Model;
import models.Other;
import models.Skim;
import models.PracticeLanguage;
import models.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;

public class UsersController extends AppController {
    private static final Pattern titlePattern = Pattern.compile("<title>([^<]+)</title>");

    public static Result me(JsonNode params) {
        User user = getMe(params);
        if (user == null)
            return Error(Error.INVALID_ACCESS_TOKEN);

        return Ok(user);
    }

    public static Result get(JsonNode params) {
        ObjectId userId = getObjectId(params, "user_id");
        User me = params.has("access_token") ? getMe(params) : null;

        Other other = Other.get(userId, me == null ? null : me);
        if (other == null)
            return Error(Error.USER_NOT_FOUND);

        User user = User.get(userId);
        if (me != null && user.getBlockings() != null && user.getBlockings().contains(me.getId()))
            return Error(Error.OBJECT_FORBIDDEN);

        return Ok(other);
    }

    public static Result getSkim(JsonNode params) {
        ObjectId userId = getObjectId(params, "user_id");

        return Ok(Skim.get(userId));
    }

    public static Result exist(JsonNode params) {
        String email = params.get("email").textValue();

        return User.getByEmail(email) != null ? Ok() : Error(Error.NOT_FOUND);
    }

    public static Result add(JsonNode params) {
        String email = params.get("email").textValue();

        if (User.getByEmail(email) != null)
            return Error(Error.USER_ALREADY_EXISTS);

        String password = params.get("password").textValue();
        String name = params.get("name").textValue();
        String nationality = params.get("nationality").textValue();
        int gender = params.get("gender").intValue();
        String birthday = params.get("birthday").textValue();

        Iterator<JsonNode> values = params.get("native_language").iterator();
        List<Integer> nativeLanguage = new ArrayList<Integer>();
        while (values.hasNext())
            nativeLanguage.add(values.next().intValue());

        values = params.get("practice_language").iterator();
        List<PracticeLanguage> practiceLanguage = new ArrayList<PracticeLanguage>();
        while (values.hasNext()) {
            JsonNode value = values.next();
            int id = value.get("id").intValue();
            int level = value.get("level").intValue();
            practiceLanguage.add(new PracticeLanguage(id, level));
        }

        User user = new User(email, password, name, nationality, gender, birthday,
                        nativeLanguage,
                        practiceLanguage);
        user.save();

        reverifyEmail(user);

        return Ok(user);
    }

    public static Result update(JsonNode params) {
        User me = getMe(params);
        me.update(params);

        return Ok();
    }

    public static Result updateAvatar(JsonNode params) {
        User me = getMe(params);
        ObjectId id = getObjectId(params, "id");

        me.updateAvatar(id);

        return Ok();
    }

    public static Result sendFriendRequest(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (user.getBlockings() != null && user.getBlockings().contains(me.getId()))
            return Error(Error.FORBIDDEN);

        me.sendFriendRequest(userId);

        return Ok();
    }

    public static Result cancelFriendRequest(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        me.cancelFriendRequest(userId);

        return Ok();
    }

    public static Result acceptFriendRequest(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        me.acceptFriendRequest(user);

        return Ok();
    }

    public static Result ignoreFriendRequest(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        me.ignoreFriendRequest(userId);

        return Ok();
    }

    public static Result unfriend(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (me.getFriends().contains(userId))
            me.unfriend(userId);

        return Ok();
    }

    public static Result follow(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (user.getBlockings() != null && user.getBlockings().contains(me.getId()))
            return Error(Error.FORBIDDEN);

        if ((me.getFriends().contains(userId)) ||
            (me.getFollowings() == null || !me.getFollowings().contains(userId)))
            me.follow(userId);

        return Ok();
    }

    public static Result unfollow(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if ((me.getFriends().contains(userId)) ||
            (me.getFollowings() != null && me.getFollowings().contains(userId)))
            me.unfollow(userId);

        return Ok();
    }

    public static Result blocking(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (me.getBlockings() == null || !me.getBlockings().contains(userId))
            me.blocking(userId);

        return Ok();
    }

    public static Result unblocking(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (me.getBlockings() != null && me.getBlockings().contains(userId))
            me.unblocking(userId);

        return Ok();
    }

    public static Result verifyEmail(JsonNode params) {
        String token = params.get("token").textValue();

        User user = User.verifyEmail(token);

        if (user == null)
            return NotFound();

        ObjectNode result = mapper.createObjectNode();
        result.put("access_token", user.newAccessToken());

        return Ok(result);
    }

    private static void reverifyEmail(User me) {
        String token = me.reverifyEmail();
        String link = webServer + "dashboard/verify-email/" + token;
        String content = views.html.Email.verify_email.render(link).toString();

        Matcher matcher = titlePattern.matcher(content);
        if(matcher.find())
            sendmail(me.getEmail(), matcher.group(1), content);
    }

    public static Result reverifyEmail(JsonNode params) {
        User me = getMe(params);

        reverifyEmail(me);

        return Ok();
    }

    public static Result loginByToken(JsonNode params) {
        String token = params.get("token").textValue();

        User user = User.getByToken(token);

        if (user == null)
            return NotFound();

        ObjectNode result = mapper.createObjectNode();
        result.put("access_token", user.newAccessToken());

        return Ok(result);
    }

    public static Result forgotPassword(JsonNode params) {
        String email = params.get("email").textValue();
        String token = User.forgetPassword(email);

        if (token == null)
            return NotFound();

        String link = webServer + "dashboard/reset-password/" + token;
        String content = views.html.Email.forgot_password.render(link).toString();

        Matcher matcher = titlePattern.matcher(content);
        if(matcher.find())
            sendmail(email, matcher.group(1), content);

        return Ok();
    }

    public static Result login(JsonNode params) {
        String email = params.get("email").textValue();
        String password = params.get("password").textValue();

        User user = User.getByEmail(email);

        if (user == null)
            return Error(Error.INCORRECT_USER);

        if (!user.getPassword().equals(Model.md5(password)))
            return Error(Error.INCORRECT_PASSWORD);

        ObjectNode result = mapper.createObjectNode();
        result.put("access_token", user.newAccessToken());

        return Ok(result);
    }

    public static Result logout(JsonNode params) {
        String token = params.get("access_token").textValue();

        User.deleteAccessToken(token);

        return Ok();
    }

    public static Result getFriendRequest(JsonNode params) {
        User me = getMe(params);
        int type = params.get("type").textValue().equals("send") ? 0 : 1;

        return Ok(me.getFriendRequest(type));
    }

    public static Result getFriend(JsonNode params) {
        int skip = params.has("skip") ? params.get("skip").intValue() : 0;
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        User user;
        if (params.has("user_id")) {
            ObjectId userId = getObjectId(params, "user_id");
            user = User.get(userId);
            if (user == null)
                return NotFound();
        } else {
            user = getMe(params);
        }

        return Ok(user.getFriend(skip, limit));
    }

    public static Result getOnlineFriend(JsonNode params) {
        User me = getMe(params);
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(me.getOnlineFriend(until, limit));
    }

    public static Result getMutualFriend(JsonNode params) {
        ObjectId userId = getObjectId(params, "user_id");
        User me = params.has("access_token") ? getMe(params) : null;
        int skip = params.has("skip") ? params.get("skip").intValue() : 0;
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        Other other = Other.get(userId, me);

        if (other == null)
            return Error(Error.USER_NOT_FOUND);

        return Ok(other.getMutualFriend(skip, limit));
    }

    public static Result getFollower(JsonNode params) {
        int skip = params.has("skip") ? params.get("skip").intValue() : 0;
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        User user;
        if (params.has("user_id")) {
            ObjectId userId = getObjectId(params, "user_id");
            user = User.get(userId);
            if (user == null)
                return NotFound();
        } else {
            user = getMe(params);
        }

        return Ok(user.getFollower(skip, limit));
    }

    public static Result getFollowing(JsonNode params) {
        int skip = params.has("skip") ? params.get("skip").intValue() : 0;
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        User user;
        if (params.has("user_id")) {
            ObjectId userId = getObjectId(params, "user_id");
            user = User.get(userId);
            if (user == null)
                return NotFound();
        } else {
            user = getMe(params);
        }

        return Ok(user.getFollowing(skip, limit));
    }

    public static Result getBlocking(JsonNode params) {
        User me = getMe(params);
        int skip = params.has("skip") ? params.get("skip").intValue() : 0;
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(me.getBlocking(skip, limit));
    }

    public static Result search(JsonNode params) {
        User me = params.has("access_token") ? getMe(params) : null;
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(User.search(me, params, new Date(until), limit));
    }

    public static Result hi(JsonNode params) {
        User me = getMe(params);
        ObjectId userId = getObjectId(params, "user_id");
        User user = User.get(userId);

        if (user == null)
            return Error(Error.USER_NOT_FOUND);

        if (userId.equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (user.getBlockings() != null && user.getBlockings().contains(me.getId()))
            return Error(Error.FORBIDDEN);

        me.hi(userId);

        return Ok();
    }
}
