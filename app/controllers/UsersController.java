package controllers;

import controllers.constants.Error;

import models.Model;
import models.Other;
import models.PracticeLanguage;
import models.User;

import java.util.ArrayList;
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

        Other other = Other.get(userId);
        if (other == null)
            return Error(Error.USER_NOT_FOUND);

        return Ok(other);
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

        User user = new User(email, password, name, nativeLanguage, practiceLanguage);
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

        if (me.getFollowings() == null || !me.getFollowings().contains(userId))
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

        if (me.getFollowings() != null && me.getFollowings().contains(userId))
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
        result.put("access_token", user.newToken());

        return Ok(result);
    }

    private static void reverifyEmail(User me) {
        String token = me.reverifyEmail();
        String link = webServer + "account/verify-email/" + token;
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

    public static Result login(JsonNode params) {
        String email = params.get("email").textValue();
        String password = params.get("password").textValue();

        User user = User.getByEmail(email);

        if (user == null)
            return Error(Error.INCORRECT_USER);

        if (!user.getPassword().equals(Model.md5(password)))
            return Error(Error.INCORRECT_PASSWORD);

        ObjectNode result = mapper.createObjectNode();
        result.put("access_token", user.newToken());

        return Ok(result);
    }

    public static Result logout(JsonNode params) {
        String token = params.get("access_token").textValue();

        User.deleteToken(token);

        return Ok();
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
        int skip = params.has("skip") ? params.get("skip").intValue() : 0;
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;
        return Ok(User.search(skip, limit));
    }
}
