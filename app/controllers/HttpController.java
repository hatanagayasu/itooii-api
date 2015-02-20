package controllers;

import play.Play;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;

import controllers.annotations.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ClassNotFoundException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class HttpController extends DispatchController
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
                            name : { ..., validations : {{}, ...} },
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
            File file = new File(Play.application().path(), "conf/http_routes");
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

                ObjectNode segs = routes.with(parts[0]);
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
}
