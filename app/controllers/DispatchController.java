package controllers;

import play.*;
import play.libs.F.*;
import play.mvc.*;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;

import controllers.annotations.*;
import controllers.constants.Error;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ClassNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DispatchController extends AppController
{
    private static Map<String,Map<String,Map<String,Map<String,Object>>>> routes = new HashMap<>();
    // method/first segment/regex_rule

    static
    {
        try
        {
            File file = new File(Play.application().path(), "conf/itooii_routes");
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

            String line;
            while ((line = bufferedReader.readLine()) != null)
            {
                if (line.startsWith("#"))
                    continue;

                // method path controller/action
                String[] parts = line.split("\\s+");
                if (!routes.containsKey(parts[0]))
                    routes.put(parts[0], new HashMap<String,Map<String,Map<String,Object>>>());

                Map<String,Map<String,Map<String,Object>>> segMap = routes.get(parts[0]);
                String[] segs = parts[1].split("/");
                int length = segs.length;
                if (!segMap.containsKey(segs[0]))
                    segMap.put(segs[0], new HashMap<String,Map<String,Object>>());

                Map<String,Object> m = new HashMap<>();
                Map<String,Integer> pathParamMap = new HashMap<>();
                for (int i = 1; i < length; i++)
                {
                    if (segs[i].startsWith(":"))
                        pathParamMap.put(segs[i].replaceAll("^:", ""), i);
                }
                m.put("pathParamMap", pathParamMap);

                String[] pair = parts[2].split("/");
                m.put("controller", pair[0]);
                m.put("action", pair[1]);

                String regex = "";
                for (int i = 1; i < length; i++)
                    regex += "/" + (segs[i].startsWith(":") ? "[^/]+" : segs[i]);

                Map<String,Map<String,Object>> regexMap = segMap.get(segs[0]);
                regexMap.put(regex, m);
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
    }

    public static play.mvc.Result index()
    {
        return ok();
    }

    public static play.mvc.Result dispatch(String path)
    {
        String method = request().method();
        Map<String,Object>m = match(method, path);
        if (m == null)
            return notFound();

        String controller = (String)m.get("controller");
        String action = (String)m.get("action");
        ObjectNode params = parseParams(path, (Map<String,Integer>)m.get("pathParamMap"));
        Result result = invoke(controller, action, params);

        int status = result.getStatus();
        Object content = result.getObject();

        if (content == null)
            return status(status);

        if (content instanceof ObjectNode)
            return status(status, (ObjectNode)content);

        return status(status, content.toString());
    }

    private static Map<String,Object> match(String method, String path)
    {
        if (!routes.containsKey(method))
            return null;

        Map<String,Map<String,Map<String,Object>>> segMap = routes.get(method);
        path = "/" + path;
        String[] segs = path.split("/");
        int length = segs.length;
        if (!segMap.containsKey(segs[0]))
            return null;

        Map<String,Map<String,Object>> regexMap = segMap.get(segs[0]);
        for (Entry<String,Map<String,Object>> entry : regexMap.entrySet())
        {
            String regex = entry.getKey();
            if (path.matches(regex))
                return entry.getValue();
        }

        return null;
    }

    private static Result invoke(String controller, String action, ObjectNode params)
    {
        try
        {
            Class<?> clazz = Class.forName("controllers." + controller);
            Method method = clazz.getMethod(action, new Class[] {JsonNode.class});

            // only websocket request has a param method
            if (!params.has("method") && method.getAnnotation(Anonymous.class) == null)
            {
                Result result = UsersController.me(params);

                if (result.getStatus() != 200)
                    return result;
            }

            Validation[] validations = method.getAnnotationsByType(Validation.class);
            for (Validation validation : validations)
            {
                String name = validation.name();
                String rule = validation.rule();

                if (!name.startsWith("@") && !params.has(name))
                    return Error(Error.MISSING_PARAM, name);

                if (!rule.isEmpty())
                {
                    name = name.replaceAll("/^@/", "");
                    String value = params.get(name).textValue();

                    if (!validation(rule, value))
                        return Error(Error.MALFORMED_PARAM, name);
                }
            }

            return (Result)method.invoke(null, new Object[] {params});
        }
        catch (ClassNotFoundException|NoSuchMethodException|IllegalAccessException e)
        {
            errorlog(e);

            return Error(Error.NOT_FOUND);
        }
        catch (InvocationTargetException e)
        {
            errorlog(e);

            return Error(Error.INTERNAL_SERVER_ERROR);
        }
    }

    private static ObjectNode parseParams(String path, Map<String,Integer> pathParamMap)
    {
        ObjectNode params = mapper.createObjectNode();

        path = "/" + path;
        String[] segs = path.split("/");

        for (Entry<String, Integer> entry : pathParamMap.entrySet())
            params.put(entry.getKey(), segs[entry.getValue()]);

        for (Entry<String, String[]> entry : request().queryString().entrySet())
        {
            String key = entry.getKey();
            String[] values = entry.getValue();

            if (values.length == 0)
            {
                params.put(key, "");
            }
            else if (values.length == 1)
            {
                params.put(key, values[0]);
            }
            else
            {
                ArrayNode node = mapper.createArrayNode();
                for (String value: values)
                    node.add(value);
                params.put(key, node);
            }
        }

        Map<String,String[]> formUrlEncoded = request().body().asFormUrlEncoded();
        if (formUrlEncoded != null)
        {
            for (Entry<String, String[]> entry : formUrlEncoded.entrySet())
            {
                String key = entry.getKey();
                String[] values = entry.getValue();

                if (values.length == 0)
                {
                    params.put(key, "");
                }
                else if (values.length == 1)
                {
                    params.put(key, values[0]);
                }
                else
                {
                    ArrayNode node = mapper.createArrayNode();
                    for (String value: values)
                        node.add(value);
                    params.put(key, node);
                }
            }
        }

        JsonNode json = request().body().asJson();
        if (json != null)
        {
            Iterator<String> fieldNames = json.fieldNames();
            while (fieldNames.hasNext())
            {
                String fieldName = fieldNames.next();
                params.put(fieldName, json.get(fieldName));
            }
        }

        MultipartFormData form = request().body().asMultipartFormData();
        if (form != null)
        {
            for (Entry<String, String[]> entry : form.asFormUrlEncoded().entrySet())
            {
                String key = entry.getKey();
                String[] values = entry.getValue();

                if (values.length == 0)
                {
                    params.put(key, "");
                }
                else if (values.length == 1)
                {
                    params.put(key, values[0]);
                    if(key.equals("json"))
                    {
                        try
                        {
                            params.putAll(mapper.readValue(values[0], ObjectNode.class));
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                }
                else
                {
                    ArrayNode node = mapper.createArrayNode();
                    for (String value: values)
                        node.add(value);
                    params.put(key, node);
                }
            }

            Iterator<FilePart> fileParts = form.getFiles().iterator();
            while (fileParts.hasNext())
            {
                FilePart filePart = fileParts.next();
                String key = filePart.getKey();
                if (key.equals("json"))
                {
                    try
                    {
                        params.putAll(mapper.readValue(filePart.getFile(), ObjectNode.class));
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                }
                else
                {
                    params.putPOJO(key, filePart);
                }
            }
        }

        return params;
    }

    private static boolean validation(String rule, String value)
    {
        String regex;
        if (rule.equals(ALPHA_NUMERIC))
        {
            regex = "[A-Za-z0-9]+";
        }
        else if (rule.equals(BOOLEAN))
        {
            regex = "(0|1|false|true)";
        }
        else if (rule.equals(EMAIL))
        {
            regex = "([a-z0-9._%+-]+)@[a-z0-9.-]+\\.[a-z]{2,4}";
        }
        else if (rule.equals(NOT_EMPTY))
        {
            regex = ".+";
        }
        else if (rule.equals(UUID))
        {
           regex = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
        }
        else if (rule.matches("^/.*/$"))
        {
            regex = rule.replaceFirst("^/", "").replaceFirst("/$", "");
        }
        else
        {
            regex = ".*";
        }

        return value.matches(regex);
    }

    private static Result wsDispatch(String token, String event)
    {
        try
        {
            ObjectNode params = mapper.readValue(event, ObjectNode.class);
            if (!params.has("method"))
                return Error(Error.MISSING_PARAM, "method");
            if (!params.has("path"))
                return Error(Error.MISSING_PARAM, "path");
            params.put("access_token", token);

            String method = params.get("method").textValue().toUpperCase();
            String path = params.get("path").textValue().replaceFirst("^/", "");

            Map<String,Object>m = match(method, path);
            if (m == null)
                return NotFound();

            String controller = (String)m.get("controller");
            String action = (String)m.get("action");
            //TODO PATH
            Result result = invoke(controller, action, params);

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
                if (token == null)
                {
                    out.write(Error(Error.MISSING_ACCESS_TOKEN).toString());
                    out.close();

                    return;
                }

                Result result = UsersController.me(token);
                if (result.getStatus() != 200)
                {
                    out.write(result.toString());
                    out.close();

                    return;
                }

                in.onMessage(new Callback<String>()
                {
                    public void invoke(String event)
                    {
                        out.write(wsDispatch(token, event).toString());
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
