package controllers;

import play.*;
import play.mvc.*;

import controllers.constants.Error;
import controllers.pair.*;

import models.Attachment;
import models.Model;
import models.User;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import org.bson.types.ObjectId;
import redis.clients.jedis.Jedis;

public class AppController extends Controller {
    public static final ObjectMapper mapper = new ObjectMapper();
    private static ConcurrentHashMap<ObjectId, UserTable> UsrTabMap =
        new ConcurrentHashMap<ObjectId, UserTable>();
    private static ArrayBlockingQueue<ObjectId> InPairQueue =
        new ArrayBlockingQueue<ObjectId>(1000);
    private static ArrayBlockingQueue<PairedTalkData> OutPairQueue =
        new ArrayBlockingQueue<PairedTalkData>(1000);

    static {
        new Thread(new CalcMatchScore(UsrTabMap, InPairQueue)).start();
        new Thread(new PairResult(UsrTabMap, OutPairQueue)).start();
        new Thread(new SendPairedUsers(OutPairQueue)).start();
    }

    public static void inPairQueue(ObjectId id) {
        try {
            InPairQueue.put(id);
        } catch (Exception e) {

        }
    }

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

    @SuppressWarnings(value = "unchecked")
    public static <T> T getObject(JsonNode params, String name) {
        return (T) ((POJONode) params.get(name)).getPojo();
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
                    ObjectId photoId = (ObjectId) getObject(attachment, "photo_id");
                    attachments.add(new Attachment(type, photoId));
                }
                else if (attachment.has("preview"))
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

    public static void sendEvent(String session, JsonNode event) {
        Jedis jedis = Model.getJedis();
        jedis.publish("session", session + "\n" + event);
        Model.returnJedis(jedis);
    }

    public static void sendEvent(ObjectId userId, JsonNode event) {
        Jedis jedis = Model.getJedis();
        jedis.publish("user", userId + "\n" + event);
        Model.returnJedis(jedis);
    }

    public static void sendEvent(JsonNode event) {
        Jedis jedis = Model.getJedis();
        jedis.publish("all", event.toString());
        Model.returnJedis(jedis);
    }

    public static long now() {
        return System.currentTimeMillis();
    }
}
