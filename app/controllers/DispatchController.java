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
import com.fasterxml.jackson.databind.node.POJONode;

public class DispatchController extends AppController
{
    private static ObjectNode routes = mapper.createObjectNode();
    /*
        {
            method : {
                first_segment : {
                    regex : {
                        method : Method,
                        anonymous : Boolean,
                        validations : {
                            name : { type : String, rule : String, require : Boolean },
                            ...
                        },
                        pathParamsMap : {
                            String name : int offset,
                            ...
                        }
                    }
                },
                ...
            },
            ...
        }
    */

    static
    {
        try
        {
            File file = new File(Play.application().path(), "conf/itooii_routes");
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

            String line;
            while ((line = bufferedReader.readLine()) != null)
            {
                if (line.startsWith("#") || line.matches("\\s*"))
                    continue;

                // method path controller/action
                String[] parts = line.split("\\s+");
                String[] segments = parts[1].split("/");
                String[] pair = parts[2].split("/");

                if (!routes.has(parts[0]))
                    routes.put(parts[0], mapper.createObjectNode());

                ObjectNode segs = (ObjectNode)routes.get(parts[0]);
                ObjectNode regexes = mapper.createObjectNode();
                ObjectNode route = mapper.createObjectNode();
                ObjectNode validations = mapper.createObjectNode();
                ObjectNode pathParamsMap = mapper.createObjectNode();

                Class<?> clazz = Class.forName("controllers." + pair[0]);
                Method method = clazz.getMethod(pair[1], new Class[] {JsonNode.class});
                route.putPOJO("method", method);

                if (method.getAnnotation(Anonymous.class) != null)
                    route.put("anonymous", true);

                for (Validation v : method.getAnnotationsByType(Validation.class))
                {
                    ObjectNode validation = mapper.createObjectNode();
                    validation.put("type", v.type());
                    validation.put("rule", v.rule());
                    validation.put("require", v.require());

                    validations.put(v.name(), validation);
                }
                route.put("validations", validations);

                for (int i = 1; i < segments.length; i++)
                {
                    if (segments[i].startsWith(":"))
                        pathParamsMap.put(segments[i].replaceAll("^:", ""), i);
                }
                if (pathParamsMap.fieldNames().hasNext())
                    route.put("pathParamsMap", pathParamsMap);

                String regex = "";
                for (int i = 1; i < segments.length; i++)
                    regex += "/" + (segments[i].startsWith(":") ? "[^/]+" : segments[i]);
                regexes.put(regex, route);

                segs.put(segments[1], regexes);
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

    public static play.mvc.Result index()
    {
        return ok();
    }

    public static play.mvc.Result dispatch(String path)
    {
        String method = request().method();
        JsonNode route = match(method, path);
        if (route == null)
            return notFound();

        ObjectNode params = parseParams();
        if (route.has("pathParamsMap"))
            parsePathParams(path, route.get("pathParamsMap"), params);

        Result result = invoke(route, params);

        int status = result.getStatus();
        Object content = result.getObject();

        if (content == null)
            return status(status);

        if (content instanceof ObjectNode)
            return status(status, (ObjectNode)content);

        return status(status, content.toString());
    }

    private static JsonNode match(String method, String path)
    {
        if (!routes.has(method))
            return null;

        JsonNode segs = routes.get(method);
        path = "/" + path;
        String[] segments = path.split("/");
        if (!segs.has(segments[1]))
            return null;

        JsonNode regexes = segs.get(segments[1]);
        Iterator<String> fieldNames = regexes.fieldNames();
        while (fieldNames.hasNext())
        {
            String regex = fieldNames.next();
            if (path.matches(regex))
                return regexes.get(regex);
        }

        return null;
    }

    private static Result invoke(JsonNode route, ObjectNode params)
    {
        try
        {
            Method method = (Method)((POJONode)route.get("method")).getPojo();

            // only websocket request has a param method
            if (!params.has("method") && !route.has("anonymous"))
            {
                Result result = UsersController.me(params);

                if (result.getStatus() != 200)
                    return result;
            }

            JsonNode validations = route.get("validations");
            Iterator<String> fieldNames = validations.fieldNames();
            while (fieldNames.hasNext())
            {
                String name = fieldNames.next();
                JsonNode validation = validations.get(name);

                if (validation.get("require").asBoolean() && !params.has(name))
                    return Error(Error.MISSING_PARAM, name);
            }

            fieldNames = params.fieldNames();
            while (fieldNames.hasNext())
            {
                String name = fieldNames.next();
                if (name.equals("access_token"))
                    continue;

                if (!validations.has(name))
                {
                    fieldNames.remove();
                    params.remove(name);

                    continue;
                }

                JsonNode validation = validations.get(name);
                String rule = validation.get("rule").textValue();

                if (!rule.isEmpty())
                {
                    String value = params.get(name).textValue();

                    if (!validation(rule, value))
                        return Error(Error.MALFORMED_PARAM, name);
                }
            }

            return (Result)method.invoke(null, new Object[] {params});
        }
        catch (IllegalAccessException e)
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

    private static void parsePathParams(String path, JsonNode pathParamsMap, ObjectNode params)
    {
        path = "/" + path;
        String[] segments = path.split("/");

        Iterator<String> fieldNames = pathParamsMap.fieldNames();
        while (fieldNames.hasNext())
        {
            String name = fieldNames.next();
            int offset = pathParamsMap.get(name).asInt();

            params.put(name, segments[offset]);
        }
    }

    private static ObjectNode parseParams()
    {
        ObjectNode params = mapper.createObjectNode();

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
        for (String r : rule.split(","))
        {
            String regex;
            if (r.equals("alphaNumeric"))
            {
                regex = "[A-Za-z0-9]+";
            }
            else if (r.equals("boolean"))
            {
                regex = "(0|1|false|true)";
            }
            else if (r.equals("email"))
            {
                regex = "([a-z0-9._%+-]+)@[a-z0-9.-]+\\.[a-z]{2,4}";
            }
            else if (r.equals("notEmpty"))
            {
                regex = ".+";
            }
            else if (r.equals("uuid"))
            {
                regex = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
            }
            else if (r.matches("^/.*/$"))
            {
                regex = r.replaceFirst("^/", "").replaceFirst("/$", "");
            }
            else if (r.matches("^/.*/$"))
            {
                regex = r.replaceFirst("^/", "").replaceFirst("/$", "");
            }
            else
            {
                if (r.startsWith("minLength="))
                {
                    r = r.replace("minLength=", "");
                    int minLength = Integer.parseInt(r);

                    if (value.length() >= minLength)
                        continue;
                }
                else if (r.startsWith("maxLength="))
                {
                    r = r.replace("maxLength=", "");
                    int maxLength = Integer.parseInt(r);

                    if (value.length() <= maxLength)
                        continue;
                }

                return false;
            }

            if (!value.matches(regex))
                return false;
        }

        return true;
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

            JsonNode route = match(method, path);
            if (route == null)
                return NotFound();

            if (route.has("pathParamsMap"))
                parsePathParams(path, route.get("pathParamsMap"), params);

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
