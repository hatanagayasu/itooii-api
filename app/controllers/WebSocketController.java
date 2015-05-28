package controllers;

import play.libs.F.*;
import play.mvc.WebSocket;

import models.Model;
import models.User;
import models.VideoChat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.types.ObjectId;
import redis.clients.jedis.JedisPubSub;

public class WebSocketController extends AppController {
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
                    HttpController.webSocketDispath(segs[1]);
            }
        }
    };

    static {
        new Thread(new Runnable() {
            public void run() {
                Model.subscribe(pubsub, "all", "user", "session", "pair");
            }
        }).start();
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

                    out.write("{\"action\":\"video/session\",\"session\":\"" + session + "\"}");
                }

                sessionToSocket.put(session, out);

                in.onMessage(new Callback<String>() {
                    public void invoke(String event) {
                        out.write(HttpController.webSocketDispath(event, session).toString());
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
