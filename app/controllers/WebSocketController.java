package controllers;

import play.Play;
import play.libs.F.*;
import play.mvc.WebSocket;

import controllers.annotations.*;
import controllers.constants.Error;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ClassNotFoundException;
import java.lang.reflect.Method;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;

public class WebSocketController extends DispatchController
{
    private static ObjectNode routes = mapper.createObjectNode();
    /*
        {
            controller : {
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
            },
            ...
        }
    */

    static
    {
        init();
    }

    private static void init()
    {
        try
        {
            File file = new File(Play.application().path(), "conf/websocket_routes");
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

            String line;
            while ((line = bufferedReader.readLine()) != null)
            {
                if (line.startsWith("#") || line.matches("\\s*"))
                    continue;

                String[] parts = line.split("\\s+");
                String[] pair = parts[0].split("/");

                if (!routes.has(pair[0]))
                    routes.put(pair[0], mapper.createObjectNode());

                ObjectNode route = mapper.createObjectNode();
                routes.with(pair[0]).put(pair[1], route);

                pair = parts[1].split("/");
                Class<?> clazz = Class.forName("controllers." + pair[0]);
                Method method = clazz.getMethod(pair[1], new Class[] {JsonNode.class});
                route.putPOJO("method", method);

                ObjectNode validations = parseValidations(method);

                if (validations.size() > 0)
                    route.put("validations", validations);
            }

            bufferedReader.close();
        }
        catch (FileNotFoundException e)
        {
            errorlog(e);
        }
        catch (IOException e)
        {
            errorlog(e);
        }
        catch (ClassNotFoundException|NoSuchMethodException e)
        {
            errorlog(e);
        }
    }

    private static JsonNode match(String controller, String action)
    {
        if (!routes.has(controller))
            return null;

        JsonNode actions = routes.get(controller);
        if (!actions.has(action))
            return null;

        return actions.get(action);
    }

    private static Result dispatch(String token, String event)
    {
        try
        {
            ObjectNode params = mapper.readValue(event, ObjectNode.class);
            if (!params.has("controller") || !params.has("action"))
                return Error(Error.SERVICE_UNAVAILABLE);
            params.put("access_token", token);

            String controller = params.get("controller").textValue();
            String action = params.get("action").textValue();

            JsonNode route = match(controller, action);
            if (route == null)
                return Error(Error.SERVICE_UNAVAILABLE);

            Result result = invoke(route, params);

            return result;
        }
        catch (IOException e)
        {
            return Error(Error.MALFORMED_JSON);
        }
        catch (Exception e)
        {
            errorlog(e);

            return Error(Error.INTERNAL_SERVER_ERROR);
        }
    }

    public static WebSocket<String> websocket()
    {
        String token = request().getQueryString("access_token");

        return new WebSocket<String>()
        {
            public void onReady(WebSocket.In<String> in, WebSocket.Out<String> out)
            {
                in.onMessage(new Callback<String>()
                {
                    public void invoke(String event)
                    {
                        out.write(dispatch(token, event).toString());
                    }
                });

                in.onClose(new Callback0()
                {
                    public void invoke()
                    {
                        System.out.println("Disconnected");
                    }
                });

                out.write("Hello!");
            }
        };
    }
}
