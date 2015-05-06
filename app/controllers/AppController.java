package controllers;

import play.*;
import play.mvc.*;

import controllers.constants.Error;

import models.Attachment;
import models.Model;
import models.User;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import org.bson.types.ObjectId;

public class AppController extends Controller {
    public static final ObjectMapper mapper = new ObjectMapper();

    public static Result Ok() {
        return new Result(200);
    }

    public static Result Ok(Model model) {
        return new Result(200, Model.toJson(model));
    }

    public static Result Ok(List<? extends Model> models) {
        return new Result(200, Model.toJson(models));
    }

    public static Result Ok(JsonNode node) {
        return new Result(200, node);
    }

    public static Result Ok(ObjectNode node) {
        return new Result(200, node);
    }

    public static Result Error(Error error) {
        ObjectNode result = mapper.createObjectNode();
        result.put("error_code", error.getCode());
        result.put("description", error.getDescription());

        return new Result(error.getCode() / 100, result);
    }

    public static Result Error(Error error, Object... params) {
        ObjectNode result = mapper.createObjectNode();
        result.put("error_code", error.getCode());
        result.put("description", String.format(error.getDescription(), params));

        return new Result(error.getCode() / 100, result);
    }

    public static Result NotFound() {
        return new Result(404);
    }

    public static void errorlog(Object error) {
        Logger.error(error.toString());
    }

    public static void errorlog(Throwable cause) {
        StringWriter errors = new StringWriter();
        cause.printStackTrace(new PrintWriter(errors));
        Logger.error(errors.toString());
    }

    public static User getMe(JsonNode params) {
        String token = params.get("access_token").textValue();

        return User.getByToken(token);
    }

    public static ObjectId getObjectId(JsonNode params, String name) {
        return (ObjectId) ((POJONode) params.get(name)).getPojo();
    }

    public static List<Attachment> getAttachments(JsonNode params) {
        List<Attachment> attachments = new ArrayList<Attachment>();
        if (params.has("attachments")) {
            Iterator<JsonNode> values = params.get("attachments").iterator();
            while (values.hasNext()) {
                JsonNode attachment = values.next();
                String type = attachment.get("type").textValue();
                if (attachment.has("photo_id"))
                {
                    String url = attachment.get("url").textValue();
                    ObjectId photoId = getObjectId(attachment, "photo_id");
                    attachments.add(new Attachment(type, url, photoId));
                }
                else if (attachment.has("url"))
                {
                    String url = attachment.get("url").textValue();
                    String preview = attachment.has("preview") ?
                        attachment.get("preview").textValue() : null;
                    attachments.add(new Attachment(type, url, preview));
                }
            }
        }

        return attachments;
    }

    public static void publish(String channel, Object event) {
        Model.publish(channel, event.toString());
    }

    public static void sendEvent(String session, JsonNode event) {
        Model.publish("session", session + "\n" + event);
    }

    public static void sendEvent(ObjectId userId, JsonNode event) {
        Model.publish("user", userId + "\n" + event);
    }

    public static void sendEvent(JsonNode event) {
        Model.publish("all", event.toString());
    }

    public static long now() {
        return System.currentTimeMillis();
    }
}
