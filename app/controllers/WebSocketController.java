package controllers;

import play.Play;
import play.libs.F.*;
import play.mvc.WebSocket;

import controllers.annotations.*;
import controllers.constants.Error;

import models.User;
import models.VideoChat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ClassNotFoundException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;

public class WebSocketController extends DispatchController {
    final public static String host = UUID.randomUUID().toString();
    final public static ConcurrentMap<String, WebSocket.Out<String>> webSocketMap = new ConcurrentHashMap();
    /*
     * { token : WebSocket.Out<String>, ... }
     * 
     * online in redis { "host:user_id" : { token : host, ... }, ... }
     * 
     * video_chat_ready in redis { "video:user_id" : "host/token", ... }
     */

    private static ObjectNode routes = mapper.createObjectNode();
    /*
     * { action : { method : Method, validations : { name : { fullName : String, type : String,
     * rules : {{}, ...}, require : Boolean }, // type array name : { ..., validation : {} }, //
     * type object name : { ..., validations : {{}, ...} }, ... } }, ... }
     */

    static {
        init();
    }

    private static void init() {
        try {
            File file = new File(Play.application().path(), "conf/websocket_routes");
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.startsWith("#") || line.matches("\\s*"))
                    continue;

                String[] parts = line.split("\\s+");
                String[] pair = parts[1].split("/");

                ObjectNode route = mapper.createObjectNode();
                routes.put(parts[0], route);

                Class<?> clazz = Class.forName("controllers." + pair[0]);
                Method method = clazz.getMethod(pair[1], new Class[] { JsonNode.class });
                route.putPOJO("method", method);

                ObjectNode validations = parseValidations(method);

                if (validations.size() > 0)
                    route.put("validations", validations);
            }

            bufferedReader.close();
        } catch (FileNotFoundException e) {
            errorlog(e);
        } catch (IOException e) {
            errorlog(e);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            errorlog(e);
        }
    }

    private static JsonNode match(String action) {
        if (!routes.has(action))
            return null;

        return routes.get(action);
    }

    private static Result dispatch(String token, String event) {
        try {
            ObjectNode params = mapper.readValue(event, ObjectNode.class);
            if (!params.has("action"))
                return Error(Error.SERVICE_UNAVAILABLE);
            params.put("access_token", token);

            String action = params.get("action").textValue();

            JsonNode route = match(action);
            if (route == null)
                return Error(Error.SERVICE_UNAVAILABLE);

            Result result = invoke(route, params);

            return result;
        } catch (IOException e) {
            return Error(Error.MALFORMED_JSON);
        } catch (Exception e) {
            errorlog(e);

            return Error(Error.INTERNAL_SERVER_ERROR);
        }
    }

    public static WebSocket<String> websocket() {
        String token = request().getQueryString("access_token");
        final String session = UUID.randomUUID().toString();

        return new WebSocket<String>() {
            public void onReady(WebSocket.In<String> in, final WebSocket.Out<String> out) {
                final String userId = models.User.getUserIdByToken(token);

                if (userId != null) {
                    User.online(userId, session, host);
                    webSocketMap.put(session, out);
                }

                in.onMessage(new Callback<String>() {
                    public void invoke(String event) {
                        out.write(dispatch(session, event).toString());
                    }
                });

                in.onClose(new Callback0() {
                    public void invoke() {
                        if (userId != null) {
                            User.offline(userId, session);
                            webSocketMap.remove(session);

                            VideoChat videoChat = VideoChat.get(new ObjectId(userId));
                            if (videoChat != null)
                                videoChat.leave();
                        }

                        System.out.println("Disconnected");
                    }
                });
            }
        };
    }
}
