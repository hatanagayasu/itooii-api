package controllers;

import play.Play;
import play.libs.F.*;
import play.mvc.WebSocket;

import controllers.constants.Error;

import models.Model;
import models.User;
import models.VideoChat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ClassNotFoundException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.types.ObjectId;
import redis.clients.jedis.JedisPubSub;

public class WebSocketController extends DispatchController {
    private static Map<String, Set<WebSocket.Out<String>>> userToSockets =
        new ConcurrentHashMap<String, Set<WebSocket.Out<String>>>();
    private static Map<String, WebSocket.Out<String>> sessionToSocket =
        new ConcurrentHashMap<String, WebSocket.Out<String>>();
    /*
        {
            user_id : [ WebSocket.Out<String>, ... ],
            ...
        }
        {
            token : WebSocket.Out<String>,
            ...
        }
    */

    private static ObjectNode routes = mapper.createObjectNode();
    /*
        {
            action : {
                method : Method,
                validations : {
                    name : {
                        fullName : String,
                        type : String,
                        rules : {{}, ...},
                        require : Boolean
                    },
                    // type array
                    name : { ..., validation : {} },
                    // type object
                    name : { ..., validations : {{}, ...} },
                    ...
                }
            },
            ...
        }
    */

    private static JedisPubSub pubsub = new JedisPubSub() {
        public void onMessage(String channel, String message) {
            if (channel.equals("all")) {
                sessionToSocket.forEach((session, out) -> out.write(message));
            } else if (channel.equals("user")) {
                String[] segs = message.split("\n");
                Set<WebSocket.Out<String>>sockets = userToSockets.get(segs[0]);
                if (sockets != null)
                    sockets.forEach(out -> out.write(segs[1]));
            } else if (channel.equals("session")) {
                String[] segs = message.split("\n");
                WebSocket.Out<String>out = sessionToSocket.get(segs[0]);
                if (out != null)
                    out.write(segs[1]);
            } else if (channel.equals("pair")) {
                String[] segs = message.split("\n");
                if (sessionToSocket.containsKey(segs[0]))
                    dispatch(null, segs[1]);
            }
        }
    };

    static {
        init();
    }

    private static void init() {
        try {
            ObjectNode validations = null;
            ObjectNode access = mapper.createObjectNode();

            access.put("fullName", "access_token")
                .put("type", "access_token")
                .put("require", true);

            File file = new File(Play.application().path(), "conf/websocket_routes");
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

            boolean accumulation = false;
            String line;
            int no = 0;
            while ((line = bufferedReader.readLine()) != null) {
                no++;

                if (!accumulation && (line.startsWith("@") || line.matches("\\s*"))) {
                    accumulation = true;

                    validations = mapper.createObjectNode();
                    validations.set("access_token", access);
                }

                if (line.startsWith("#") || line.matches("\\s*"))
                    continue;

                if (line.startsWith("@Anonymous")) {
                    validations.remove("access_token");
                } else if (line.startsWith("@Validation")) {
                    parseVlidation(line, no, validations);
                } else {
                    accumulation = false;

                    String[] parts = line.split("\\s+");
                    String[] pair = parts[1].split("/");

                    ObjectNode route = routes.putObject(parts[0]);

                    Class<?> clazz = Class.forName("controllers." + pair[0]);
                    Method method = clazz.getMethod(pair[1], new Class[] { JsonNode.class });
                    route.putPOJO("method", method);

                    if (validations.size() > 0)
                        route.set("validations", validations);
                }
            }

            bufferedReader.close();
        } catch (FileNotFoundException e) {
            errorlog(e);
        } catch (IOException e) {
            errorlog(e);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            errorlog(e);
        }

        new Thread(new Runnable() {
            public void run() {
                Model.subscribe(pubsub, "all", "user", "session", "pair");
            }
        }).start();
    }

    private static Result dispatch(String token, String event) {
        try {
            ObjectNode params = mapper.readValue(event, ObjectNode.class);
            if (!params.has("action"))
                return Error(Error.SERVICE_UNAVAILABLE);
            params.put("access_token", token);

            String action = params.get("action").textValue();

            JsonNode route = routes.get(action);
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
                final String userId = User.getUserIdByToken(token);

                if (userId != null) {
                    User.newToken(userId, session);

                    Set<WebSocket.Out<String>>sockets = userToSockets.get(userId);
                    if (sockets == null) {
                        sockets = Collections.synchronizedSet(new HashSet<WebSocket.Out<String>>());
                        userToSockets.put(userId, sockets);
                    }
                    sockets.add(out);
                }

                sessionToSocket.put(session, out);

                in.onMessage(new Callback<String>() {
                    public void invoke(String event) {
                        out.write(dispatch(session, event).toString());
                    }
                });

                in.onClose(new Callback0() {
                    public void invoke() {
                        sessionToSocket.remove(session);

                        if (userId != null) {
                            Set<WebSocket.Out<String>>sockets = userToSockets.get(userId);
                            sockets.remove(out);
                            if (sockets.isEmpty())
                                userToSockets.remove(userId);

                            User.deleteToken(session);

                            VideoChat videoChat = VideoChat.get(new ObjectId(userId));
                            if (videoChat != null)
                                videoChat.leave();
                        }
                    }
                });
            }
        };
    }
}
