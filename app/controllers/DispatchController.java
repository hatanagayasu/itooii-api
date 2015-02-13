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

    public static Result dispatch(String path)
    {
        String method = request().method();
        if (!routes.containsKey(method))
            return error(Error.NOT_FOUND);

        Map<String,Map<String,Map<String,Object>>> segMap = routes.get(method);
        path = "/" + path;
        String[] segs = path.split("/");
        int length = segs.length;
        if (!segMap.containsKey(segs[0]))
            return error(Error.NOT_FOUND);

        Map<String,Map<String,Object>> regexMap = segMap.get(segs[0]);
        for (Entry<String,Map<String,Object>> entry : regexMap.entrySet())
        {
            String regex = entry.getKey();
            if (path.matches(regex))
            {
                Map<String,Object> m = entry.getValue();

                String controller = (String)m.get("controller");
                String action = (String)m.get("action");
                ObjectNode params = parseParams(segs, (Map<String,Integer>)m.get("pathParamMap"));

                return invoke(controller, action, params);
            }
        }

        return error(Error.NOT_FOUND);
    }

    private static Result invoke(String controller, String action, ObjectNode params)
    {
        try
        {
            Class<?> clazz = Class.forName("controllers." + controller);
            Method method = clazz.getMethod(action, new Class[] {JsonNode.class});

            Validation[] validations = method.getAnnotationsByType(Validation.class);
            for (Validation validation : validations)
            {
                String name = validation.name();
                String rule = validation.rule();

                if (!name.startsWith("@") && !params.has(name))
                    return error(Error.MISSING_PARAM, name);

                if (!rule.isEmpty())
                {
                    name = name.replaceAll("/^@/", "");
                    String value = params.get(name).textValue();

                    if (!validation(rule, value))
                        return error(Error.MALFORMED_PARAM, name);
                }
            }

            return (Result)method.invoke(null, new Object[] {params});
        }
        catch (ClassNotFoundException|NoSuchMethodException|IllegalAccessException e)
        {
            errorlog(e);

            return error(Error.NOT_FOUND);
        }
        catch (InvocationTargetException e)
        {
            errorlog(e);

            return error(Error.INTERNAL_SERVER_ERROR);
        }
    }

    private static ObjectNode parseParams(String[] segs, Map<String,Integer> pathParamMap)
    {
        ObjectNode params = mapper.createObjectNode();

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

    public static WebSocket<String> websocket()
    {
        return new WebSocket<String>()
        {
            public void onReady(WebSocket.In<String> in, WebSocket.Out<String> out)
            {
                in.onMessage(new Callback<String>()
                {
                    public void invoke(String event)
                    {
                        System.out.println(event);
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
