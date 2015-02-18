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
                            name : {
                                fullName : String,
                                type : String,
                                rules : {{}, ...},
                                require : Boolean
                            },
                            // type array
                            name : { ..., validation : {} },
                            // type object
                            name : { ..., validations : {{], ...} },
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
        init();
    }

    private static void init()
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
                ObjectNode pathParamsMap = mapper.createObjectNode();

                Class<?> clazz = Class.forName("controllers." + pair[0]);
                Method method = clazz.getMethod(pair[1], new Class[] {JsonNode.class});
                route.putPOJO("method", method);

                if (method.getAnnotation(Anonymous.class) != null)
                    route.put("anonymous", true);

                ObjectNode validations = parseValidations(method);
                if (validations.size() > 0)
                    route.put("validations", validations);

                for (int i = 1; i < segments.length; i++)
                {
                    if (segments[i].startsWith(":"))
                        pathParamsMap.put(segments[i].replaceAll("^:", ""), i);
                }
                if (pathParamsMap.size() > 0)
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

    private static ObjectNode parseValidations(Method method)
    {
        ObjectNode validations = mapper.createObjectNode();

        for (Validation v : method.getAnnotationsByType(Validation.class))
        {
            ObjectNode validation = mapper.createObjectNode();
            String name = v.name();
            String type = v.type();
            validation.put("fullName", name);
            validation.put("type", type);
            validation.put("require", v.require());

            if (!v.rule().isEmpty() && (type.equals("string") || type.equals("integer")))
            {
                ObjectNode rules = mapper.createObjectNode();
                for (String rule : v.rule().split(","))
                {
                    if (rule.contains("=") && !rule.startsWith("/"))
                    {
                        //TODO
                        String[] pair = rule.split("=");
                        rules.put(pair[0], Integer.parseInt(pair[1]));
                    }
                    else
                    {
                        rules.put(rule, false);
                    }
                }
                validation.put("rules", rules);
            }

            if (type.equals("object"))
                validation.put("validations", mapper.createObjectNode());

            JsonNode parent = validations;
            String[] segs = name.split("\\.");
            if (segs.length > 1)
            {
                for (int i = 0; i < segs.length - 1; i++)
                {
                    if (segs[i].endsWith("[]"))
                    {
                        parent = parent.get(segs[i].replace("[]", "")).get("validation");
                        parent = parent.get("validations");
                    }
                    else
                    {
                        parent = parent.get(segs[i]).get("validations");
                    }
                }
            }

            if (name.endsWith("[]"))
            {
                parent = parent.get(name.replace("[]", ""));
                ((ObjectNode)parent).put("validation", validation);
            }
            else
            {
                ((ObjectNode)parent).put(segs[segs.length - 1], validation);
            }
        }

        return validations;
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

    private static class MissingParamException extends Exception
    {
        MissingParamException(JsonNode validation)
        {
            super(validation.get("fullName").textValue());
        }
    }

    private static class MalformedParamException extends Exception
    {
        MalformedParamException(JsonNode validation)
        {
            super(validation.get("fullName").textValue());
        }
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

            if (route.has("validations"))
                validations(route.get("validations"), params);

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
        catch (MissingParamException e)
        {
            return Error(Error.MISSING_PARAM, e.getMessage());
        }
        catch (MalformedParamException e)
        {
            return Error(Error.MALFORMED_PARAM, e.getMessage());
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
            int offset = pathParamsMap.get(name).intValue();

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

    private static void validations(JsonNode validations, ObjectNode params)
        throws MissingParamException, MalformedParamException
    {
        Iterator<String> fieldNames = validations.fieldNames();
        while (fieldNames.hasNext())
        {
            String name = fieldNames.next();
            JsonNode validation = validations.get(name);

            if (validation.get("require").booleanValue() && !params.has(name))
                throw new MissingParamException(validation);
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
            String type = validation.get("type").textValue();
            JsonNode param = params.get(name);

            if (type.equals("object"))
            {
                if (!param.isObject())
                    throw new MalformedParamException(validation);

                validations(validation.get("validations"), (ObjectNode)param);
            }
            else if (type.equals("array"))
            {
                if (!param.isArray())
                    throw new MalformedParamException(validation);

                JsonNode v = validation.get("validation");

                Iterator<JsonNode> values = param.iterator();
                while (values.hasNext())
                    validation(v, values.next());
            }
            else
            {
                validation(validation, param);
            }
        }
    }

    private static void validation(JsonNode validation, JsonNode param)
        throws MissingParamException, MalformedParamException
    {
        String type = validation.get("type").textValue();

        if (type.equals("string"))
        {
            if (!param.isTextual() || param.textValue().isEmpty())
                throw new MalformedParamException(validation);

            if (validation.has("rules") && !validation(validation.get("rules"), param.textValue()))
                throw new MalformedParamException(validation);
        }
        else if (type.equals("boolean"))
        {
            if (!param.isBoolean())
                throw new MalformedParamException(validation);
        }
        else if (type.equals("integer"))
        {
            if (!param.isInt())
                throw new MalformedParamException(validation);

            if (validation.has("rules") && !validation(validation.get("rules"), param.intValue()))
                throw new MalformedParamException(validation);
        }
        else if (type.equals("object"))
        {
            if (!param.isObject())
                throw new MalformedParamException(validation);

            if (validation.has("validations"))
                validations(validation.get("validations"), (ObjectNode)param);
        }
        else
        {
            throw new MalformedParamException(validation);
        }
    }

    private static boolean validation(JsonNode rules, int value)
    {
        Iterator<String> iterator = rules.fieldNames();
        while (iterator.hasNext())
        {
            String rule = iterator.next();

            if (rule.equals("min"))
            {
                if (value < rules.get(rule).intValue())
                    return false;
            }
            else if (rule.equals("max"))
            {
                if (value > rules.get(rule).intValue())
                    return false;
            }
            else
            {
                return false;
            }
        }

        return true;
    }

    private static boolean validation(JsonNode rules, String value)
    {
        Iterator<String> iterator = rules.fieldNames();
        while (iterator.hasNext())
        {
            String rule = iterator.next();

            String regex;
            if (rule.equals("alphaNumeric"))
            {
                regex = "[A-Za-z0-9]+";
            }
            else if (rule.equals("email"))
            {
                regex = "([a-z0-9._%+-]+)@[a-z0-9.-]+\\.[a-z]{2,4}";
            }
            else if (rule.equals("uuid"))
            {
                regex = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
            }
            else if (rule.matches("^/.*/$"))
            {
                regex = rule.replaceFirst("^/", "").replaceFirst("/$", "");
            }
            else if (rule.equals("minLength"))
            {
                if (value.length() >= rules.get(rule).intValue())
                    continue;
                else
                    return false;
            }
            else if (rule.equals("maxLength"))
            {
                if (value.length() <= rules.get(rule).intValue())
                    continue;
                else
                    return false;
            }
            else
            {
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
