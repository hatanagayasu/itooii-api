package controllers;

import play.*;
import play.mvc.*;

import controllers.constants.Error;
import controllers.exceptions.InvalidSigningException;

import models.Attachment;
import models.AttachmentType;
import models.Model;
import models.User;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import org.bson.types.ObjectId;

public class AppController extends Controller {
    public static final ObjectMapper mapper = new ObjectMapper();
    public static final Properties props = Model.props;
    public static final String webServer = props.getProperty("web_server");
    private static final boolean sendmail =
        Boolean.parseBoolean(props.getProperty("sendmail", "false"));

    public static Result Ok() {
        return new Result(200);
    }

    public static Result Ok(Model model) {
        return new Result(200, Model.toJson(model));
    }

    public static Result Ok(List<? extends Model> models) {
        return new Result(200, Model.toJson(models));
    }

    public static Result Ok(Object object) {
        return new Result(200, object);
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

    public static Result ObjectForbidden() {
        return Error(Error.OBJECT_FORBIDDEN);
    }

    public static Result NotModified() {
        return new Result(304);
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

        return User.getByAccessToken(token);
    }

    public static ObjectId getObjectId(JsonNode params, String name) {
        POJONode pojo = (POJONode)params.get(name);
        return pojo == null ? null : (ObjectId) pojo.getPojo();
    }

    public static Date getDate(JsonNode params, String name) {
        return (Date) ((POJONode) params.get(name)).getPojo();
    }

    public static List<Attachment> getAttachments(JsonNode params) {
        List<Attachment> attachments = new ArrayList<Attachment>();
        if (params.has("attachments")) {
            Iterator<JsonNode> values = params.get("attachments").iterator();
            while (values.hasNext()) {
                JsonNode attachment = values.next();
                AttachmentType type = AttachmentType.valueOf(attachment.get("type").textValue());
                if (type == AttachmentType.photo) {
                    ObjectId id = getObjectId(attachment, "id");
                    int width = attachment.get("width").intValue();
                    int height = attachment.get("height").intValue();
                    String signing = attachment.get("signing").textValue();

                    if (!Model.md5("#" + id + width + height).equals(signing))
                        throw new RuntimeException(new InvalidSigningException());

                    attachments.add(new Attachment(type, id, width, height));
                } else if (type == AttachmentType.url) {
                    String url = attachment.get("url").textValue();
                    attachments.add(new Attachment(type, url));
                } else if (type == AttachmentType.audio) {
                    ObjectId id = getObjectId(attachment, "id");
                    int bit_rate = attachment.get("bit_rate").intValue();
                    double duration = attachment.get("duration").doubleValue();
                    String signing = attachment.get("signing").textValue();

                    if (!Model.md5("#" + id + bit_rate + duration).equals(signing))
                        throw new RuntimeException(new InvalidSigningException());

                    attachments.add(new Attachment(type, id, bit_rate, duration));
                } else if (type == AttachmentType.video) {
                    ObjectId id = getObjectId(attachment, "id");
                    attachments.add(new Attachment(type, id));
                }
            }
        }

        return attachments.size() > 0 ? attachments : null;
    }

    public static void publish(String channel, Object event) {
        Model.publish(channel, event.toString());
    }

    public static void sendEvent(String session, Object event) {
        Model.publish("session", session + "\n" + event);
    }

    public static void sendEvent(ObjectId userId, Object event) {
        Model.publish("user", userId + "\n" + event);
    }

    public static void sendEvent(Object event) {
        Model.publish("all", event.toString());
    }

    public static void sendmail(String to, String subject, String content) {
        if (sendmail) {
            Model.sendmail(to, subject, content);
        } else {
            Logger.debug(to);
            Logger.debug(subject);
            Logger.debug(content);
        }
    }

    public static long now() {
        return System.currentTimeMillis();
    }
}
