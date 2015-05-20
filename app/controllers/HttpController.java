package controllers;

import play.Play;

import controllers.constants.Error;

import models.Privilege;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ClassNotFoundException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class HttpController extends DispatchController {
    private static ObjectNode routes = mapper.createObjectNode();
    private static ObjectNode macros = mapper.createObjectNode();

    /*
        {
            first_segment : {
                regex : {
                    method : {
                        method : Method,
                        cache_control : String,
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
                        path_params_map : {
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
            ObjectNode validations = null;
            int maxAge = 0;

            File file = new File(Play.application().path(), "conf/http_routes");
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

            boolean accumulation = false;
            String line;
            int no = 0;
            while ((line = bufferedReader.readLine()) != null) {
                no++;

                if (!accumulation && (line.startsWith("@") || line.matches("\\s*"))) {
                    accumulation = true;

                    validations = mapper.createObjectNode();
                    validations.putObject("access_token")
                        .put("fullName", "access_token")
                        .put("type", "access_token")
                        .put("require", true)
                        .put("privilege", Privilege.Observer.value());

                    maxAge = 0;
                }

                if (line.startsWith("#") || line.matches("\\s*"))
                    continue;

                if (line.startsWith("@Anonymous")) {
                    validations.remove("access_token");
                } else if (line.startsWith("@Privilege")) {
                    Matcher matcher = parenthesesPattern.matcher(line);
                    if (matcher.find()) {
                        int privilege = Privilege.valueOf(matcher.group(1)).value();
                        validations.with("access_token").put("privilege", privilege);
                    }
                } else if (line.startsWith("@CacheControl")) {
                    Matcher matcher = parenthesesPattern.matcher(line);
                    maxAge = matcher.find() ? Integer.parseInt(matcher.group(1)) : 3600;
                } else if (line.startsWith("@Macro")) {
                    accumulation = false;

                    if (validations.has("access_token"))
                        validations.remove("access_token");

                    if (validations.size() > 0) {
                        Matcher matcher = parenthesesPattern.matcher(line);

                        if (matcher.find())
                            macros.set(matcher.group(1), validations);
                    }
                } else if (line.startsWith("@Validation")) {
                    parseVlidation(line, no, validations);
                } else if (line.startsWith("@")) {
                    String name = line.substring(1);
                    if (!macros.has(name)) {
                        errorlog("No Such Macro " + name + " at line " + no);
                        break;
                    }


                    JsonNode macro = macros.get(name);
                    Iterator<String> fieldNames = macro.fieldNames();
                    while (fieldNames.hasNext()) {
                        String fieldName = fieldNames.next();
                        validations.set(fieldName, macro.get(fieldName));
                    }
                } else {
                    accumulation = false;
                    parseRoute(line, validations, maxAge);
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
    }

    private static void parseRoute(String line, ObjectNode validations, int maxAge)
        throws ClassNotFoundException, NoSuchMethodException {
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

        if (maxAge > 0)
            route.put("cache_control", maxAge);

        if (validations.size() > 0)
            route.set("validations", validations);

        for (int i = 1; i < segs.length; i++) {
            if (segs[i].startsWith("@")) {
                pathParamsMap.put(segs[i].substring(1), i);
                segs[i] = "[0-9a-fA-F]{24}";
            } else if (segs[i].startsWith(":")) {
                pathParamsMap.put(segs[i].substring(1), i);
                segs[i] = "[^/]+";
            }
        }
        if (pathParamsMap.size() > 0)
            route.set("path_params_map", pathParamsMap);

        String regex = "";
        for (int i = 1; i < segs.length; i++)
            regex += "/" + segs[i];

        if (!regexes.has(regex))
            regexes.putObject(regex);

        regexes.with(regex).set(parts[0], route);
    }

    public static play.mvc.Result index() {
        return ok();
    }

    public static play.mvc.Result options(String path) {
        play.mvc.Http.Response response = response();

        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods",
            "GET, DELETE, HEAD, OPTIONS, POST, PUT");
        response.setHeader("Access-Control-Allow-Headers",
            "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent");

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

        if (content == null) {
            response().setHeader("Access-Control-Allow-Origin", "*");

            return status(status);
        } else if (content instanceof File) {
            response().setContentType("image/jpg");

            return ok((File) content, true);
        } else {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStream os = new GZIPOutputStream(baos);
                os.write(content.toString().getBytes());
                os.close();

                response().setHeader("Access-Control-Allow-Origin", "*");
                response().setContentType("application/json; charset=utf-8");
                response().setHeader("Content-Encoding", "gzip");

                return status(status, baos.toByteArray());
            } catch (IOException e) {
                errorlog(e);

                return internalServerError();
            }
        }
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

        if (route.has("path_params_map")) {
            JsonNode pathParamsMap = route.get("path_params_map");
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
