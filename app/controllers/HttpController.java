package controllers;

import play.Play;

import controllers.constants.Error;

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

public class HttpController extends DispatchController {
    private static ObjectNode routes = mapper.createObjectNode();
    /*
        {
            first_segment : {
                regex : {
                    method : {
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

    static {
        init();
    }

    private static void init() {
        try {
            File file = new File(Play.application().path(), "conf/http_routes");
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.startsWith("#") || line.matches("\\s*"))
                    continue;

                // method path controller/action
                String[] parts = line.split("\\s+");
                String[] segs = parts[1].split("/");
                String[] pair = parts[2].split("/");

                if (!routes.has(segs[1]))
                    routes.putObject(segs[1]);

                ObjectNode regexes = routes.with(segs[1]);
                ObjectNode route = mapper.createObjectNode();
                ObjectNode pathParamsMap = mapper.createObjectNode();

                Class<?> clazz = Class.forName("controllers." + pair[0]);
                Method method = clazz.getMethod(pair[1], new Class[] { JsonNode.class });
                route.putPOJO("method", method);

                ObjectNode validations = parseValidations(method);

                if (validations.size() > 0)
                    route.set("validations", validations);

                for (int i = 1; i < segs.length; i++) {
                    if (segs[i].startsWith(":"))
                        pathParamsMap.put(segs[i].replaceAll("^:", ""), i);
                }
                if (pathParamsMap.size() > 0)
                    route.set("pathParamsMap", pathParamsMap);

                String regex = "";
                for (int i = 1; i < segs.length; i++)
                    regex += "/" + (segs[i].startsWith(":") ? "[0-9a-fA-F]{24}" : segs[i]);

                if (!regexes.has(regex))
                    regexes.putObject(regex);

                regexes.with(regex).set(parts[0], route);
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

    public static play.mvc.Result index() {
        return ok();
    }

    private static class ServiceUnavailableException extends Exception {
        private static final long serialVersionUID = -1;
    }

    private static class MethodNotAllowedException extends Exception {
        private static final long serialVersionUID = -1;
    }

    private static play.mvc.Result convertResult(Result result) {
        int status = result.getStatus();
        Object content = result.getObject();

        play.mvc.Http.Response response = response();
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setContentType("application/json; charset=utf-8");

        if (content == null)
            return status(status);

        if (content instanceof ObjectNode)
            return status(status, (ObjectNode) content);

        return status(status, content.toString());
    }

    public static play.mvc.Result dispatch(String path) {
        try {
            String method = request().method();
            JsonNode route = match(method, path);

            ObjectNode params = parseParams(route, path);

            Result result = invoke(route, params);

            return convertResult(result);
        } catch (ServiceUnavailableException e) {
            return convertResult(Error(Error.SERVICE_UNAVAILABLE));
        } catch (MethodNotAllowedException e) {
            return convertResult(Error(Error.METHOD_NOT_ALLOWED));
        }
    }

    private static JsonNode match(String method, String path)
        throws ServiceUnavailableException, MethodNotAllowedException {
        path = "/" + path;
        String[] segs = path.split("/");

        if (!routes.has(segs[1]))
            throw new ServiceUnavailableException();

        JsonNode regexes = routes.get(segs[1]);
        Iterator<String> fieldNames = regexes.fieldNames();
        while (fieldNames.hasNext()) {
            String regex = fieldNames.next();
            if (path.matches(regex)) {
                if (!regexes.get(regex).has(method))
                    throw new MethodNotAllowedException();

                return regexes.get(regex).get(method);
            }
        }

        throw new ServiceUnavailableException();
    }

    // path parameter < query string < application/x-www-form-urlencoded < body json
    private static ObjectNode parseParams(JsonNode route, String path) {
        ObjectNode params = mapper.createObjectNode();

        if (route.has("pathParamsMap")) {
            JsonNode pathParamsMap = route.get("pathParamsMap");
            path = "/" + path;
            String[] segs = path.split("/");

            Iterator<String> fieldNames = pathParamsMap.fieldNames();
            while (fieldNames.hasNext()) {
                String name = fieldNames.next();
                int offset = pathParamsMap.get(name).intValue();

                params.put(name, segs[offset]);
            }
        }

        for (Entry<String, String[]> entry : request().queryString().entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();

            if (values.length == 0) {
                params.put(key, "");
            } else if (values.length == 1) {
                params.put(key, values[0]);
            } else {
                ArrayNode node = params.putArray(key);
                for (String value : values)
                    node.add(value);
            }
        }

        Map<String, String[]> formUrlEncoded = request().body().asFormUrlEncoded();
        if (formUrlEncoded != null) {
            for (Entry<String, String[]> entry : formUrlEncoded.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();

                if (values.length == 0) {
                    params.put(key, "");
                } else if (values.length == 1) {
                    params.put(key, values[0]);
                } else {
                    ArrayNode node = params.putArray(key);
                    for (String value : values)
                        node.add(value);
                }
            }
        }

        JsonNode json = request().body().asJson();
        if (json != null) {
            Iterator<String> fieldNames = json.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                params.set(fieldName, json.get(fieldName));
            }
        }

        return params;
    }
}
