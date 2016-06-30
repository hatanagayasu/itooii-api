package controllers;

import controllers.constants.Error;
import controllers.exceptions.InvalidSigningException;
import controllers.exceptions.ObjectForbiddenException;

import models.Privilege;
import models.User;
import models.admin.Employee;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ClassNotFoundException;
import java.lang.NumberFormatException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import com.mongodb.DuplicateKeyException;
import org.bson.types.ObjectId;

public class HttpController extends AppController {
    private static ObjectNode routes = mapper.createObjectNode();
    private static ObjectNode macros = mapper.createObjectNode();
    private static final Pattern parenthesesPattern = Pattern.compile("\\(\\s*(.+)\\s*\\)"),
        pairPattern = Pattern.compile("([^\\s=,]+)\\s*=\\s*(\"[^\"]+\"|[^\\s,\"]+)");

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
        parseRoutes("main");
    }

    private static void parseRoutes(String conf) {
        try {
            ObjectNode validations = null;
            int maxAge = 0;

            File file = new File("conf/http_routes/" + conf);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

            boolean admin = conf.matches("^admin/.*");
            boolean accumulation = false;
            String line;
            int no = 0;
            while ((line = bufferedReader.readLine()) != null) {
                no++;

                if (!accumulation && (line.startsWith("@") || line.matches("\\s*"))) {
                    accumulation = true;

                    validations = mapper.createObjectNode();
                    validations.putObject("access_token")
                        .put("admin", admin)
                        .put("fullName", "access_token")
                        .put("type", "access_token")
                        .put("require", true)
                        .put("privilege", Privilege.Observer.value());

                    maxAge = 0;
                }

                if (line.startsWith("#") || line.matches("\\s*"))
                    continue;

                if (line.startsWith("@Anonymous")) {
                    validations.with("access_token").put("require", false);
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
                } else if (line.startsWith("@Include")) {
                    Matcher matcher = parenthesesPattern.matcher(line);
                    if (matcher.find())
                        parseRoutes(matcher.group(1));
                } else if (line.startsWith("@")) {
                    String name = line.substring(1);
                    if (!macros.has(name)) {
                        errorlog("No Such Macro " + name + " at " + conf + " line " + no);
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
                    parseRoute(line, validations, admin, maxAge);
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

    private static void parseRule(String value, ObjectNode rules) {
        for (String rule : value.split(",")) {
            if (rule.contains("=") && !rule.startsWith("/")) {
                //TODO
                String[] pair = rule.split("=");
                rules.put(pair[0], Integer.parseInt(pair[1]));
            } else {
                rules.put(rule, false);
            }
        }
    }

    private static void parseVlidation(String line, int no, ObjectNode validations) {
        Matcher matcher = parenthesesPattern.matcher(line);
        if(!matcher.find()) {
            errorlog("Malformed Vlidation at line " + no);
            return;
        }

        ObjectNode validation = mapper.createObjectNode();

        matcher = pairPattern.matcher(matcher.group(1));
        while(matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2).replaceAll("(^\"|\"$)", "");

            if (key.equals("name")) {
                validation.put("fullName", value);
            } else if (key.equals("type")) {
                validation.put("type", value);

                if (value.equals("object"))
                    validation.putObject("validations");
            } else if (key.equals("rule")) {
                parseRule(value, validation.putObject("rules"));
            } else if (key.equals("depend")) {
                ObjectNode depend = validation.putObject("depend");
                String[] segs = value.split("=");
                depend.put("name", segs[0]);
                if (segs.length == 2) {
                    if (segs[1].matches("^\\(.*\\)$")) {
                        String[] values = segs[1].substring(1, segs[1].length() - 1).split("\\|");
                        ObjectNode node = depend.putObject("value");
                        for (String v : values)
                            node.put(v, false);
                    } else {
                        depend.put("value", segs[1]);
                    }
                }
            } else if (key.equals("require")) {
                validation.put("require", Boolean.parseBoolean(value));
            } else {
                errorlog("Unknown attribute " + key + " at line " + no);
            }
        }

        if (!validation.has("fullName")) {
            errorlog("Validation with out name at line " + no);
            return;
        }

        if (!validation.has("type"))
            validation.put("type", "string");

        if (!validation.has("require"))
            validation.put("require", false);

        ObjectNode parent = validations;
        String name = validation.get("fullName").textValue();
        String[] segs = name.split("\\.");
        if (segs.length > 1) {
            for (int i = 0; i < segs.length - 1; i++) {
                if (segs[i].endsWith("[]")) {
                    parent = parent.with(segs[i].replace("[]", "")).with("validation");
                    parent = parent.with("validations");
                } else {
                    parent = parent.with(segs[i]).with("validations");
                }
            }
        }

        if (name.endsWith("[]"))
            parent.with(name.replace("[]", "")).set("validation", validation);
        else
            parent.set(segs[segs.length - 1], validation);
    }

    private static void parseRoute(String line, ObjectNode validations, boolean admin, int maxAge)
        throws ClassNotFoundException, NoSuchMethodException {
        // method path controller/action
        String[] parts = line.split("\\s+");
        if (admin)
            parts[1] = "/admin" + parts[1];
        String[] segs = parts[1].split("/");
        String[] pair = parts[2].split("/");

        if (!routes.has(segs[1]))
            routes.putObject(segs[1]);

        ObjectNode regexes = routes.with(segs[1]);
        ObjectNode route = mapper.createObjectNode();
        ObjectNode pathParamsMap = mapper.createObjectNode();

        Class<?> clazz = Class.forName("controllers." + (admin ? "admin." : "") + pair[0]);
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

    public play.mvc.Result index() {
        return ok();
    }

    public play.mvc.Result options(String path) {
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

    private static class MissingParamException extends Exception {
        private static final long serialVersionUID = -1;

        MissingParamException(JsonNode validation) {
            super(validation.get("fullName").textValue());
        }
    }

    private static class MalformedParamException extends Exception {
        private static final long serialVersionUID = -1;

        MalformedParamException(JsonNode validation) {
            super(validation.get("fullName").textValue());
        }
    }

    private static class InvalidAccessTokenException extends Exception {
        private static final long serialVersionUID = -1;
    }

    private static class ForbiddenException extends Exception {
        private static final long serialVersionUID = -1;
    }

    private static Result invoke(JsonNode route, ObjectNode params) {
        try {
            Method method = (Method) ((POJONode) route.get("method")).getPojo();

            if (route.has("validations"))
                validations(route.get("validations"), params);

            Result result = (Result) method.invoke(null, new Object[] { params });

            if (route.has("cache_control") && result.getStatus() == 200) {
                int maxAge = route.get("cache_control").intValue();
                response().setHeader("Cache-Control", "max-age=" + maxAge);
            }

            return result;
        } catch (IllegalAccessException e) {
            errorlog(e);

            return Error(Error.NOT_FOUND);
        } catch (InvocationTargetException e) {
            Class<? extends Throwable> clazz = e.getCause().getClass();
            if (clazz == DuplicateKeyException.class) {
                return Error(Error.MONGO_DUPLICATE_KEY, e.getCause());
            } else if (clazz == RuntimeException.class) {
                if (e.getCause().getCause().getClass() == InvalidSigningException.class)
                    return Error(Error.INVALID_SIGNING_EXCEPTION);
                if (e.getCause().getCause().getClass() == ObjectForbiddenException.class)
                    return Error(Error.OBJECT_FORBIDDEN);
            }

            errorlog(e);

            return Error(Error.INTERNAL_SERVER_ERROR);
        } catch (MissingParamException e) {
            return Error(Error.MISSING_PARAM, e.getMessage());
        } catch (MalformedParamException e) {
            return Error(Error.MALFORMED_PARAM, e.getMessage());
        } catch (InvalidAccessTokenException e) {
            return Error(Error.INVALID_ACCESS_TOKEN);
        } catch (ForbiddenException e) {
            return Error(Error.FORBIDDEN);
        }
    }

    private static void validationDepend(JsonNode validation, ObjectNode params, String name)
        throws MissingParamException {
        JsonNode depend = validation.get("depend");
        String field = depend.get("name").textValue();

        if (depend.has("value")) {
            JsonNode value = depend.get("value");
            if (value.isTextual()) {
                if (params.has(field) && value.textValue().equals(params.get(field).textValue())) {
                    if (!params.has(name))
                        throw new MissingParamException(validation);
                } else {
                    params.remove(name);
                }
            } else {
                if (params.has(field) && value.has(params.get(field).textValue())) {
                    if (!params.has(name))
                        throw new MissingParamException(validation);
                } else {
                    params.remove(name);
                }
            }
        } else {
            if (field.startsWith("|")) {
                field = field.replaceFirst("^\\|", "");
                if (!params.has(field) && !params.has(name))
                    throw new MissingParamException(validation);
            } else if (field.startsWith("!")) {
                field = field.replaceFirst("^!", "");
                if (!params.has(field)) {
                    if (!params.has(name))
                        throw new MissingParamException(validation);
                } else {
                    params.remove(name);
                }
            } else {
                if (params.has(field)) {
                    if (!params.has(name))
                        throw new MissingParamException(validation);
                } else {
                    params.remove(name);
                }
            }
        }
    }

    private static void validations(JsonNode validations, ObjectNode params)
        throws MissingParamException, MalformedParamException, InvalidAccessTokenException,
        ForbiddenException {

        Iterator<String> fieldNames = validations.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            JsonNode validation = validations.get(name);

            if (validation.get("require").booleanValue() && !params.has(name))
                throw new MissingParamException(validation);

            if (validation.has("depend"))
                validationDepend(validation, params, name);
        }

        fieldNames = params.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();

            if (!validations.has(name)) {
                fieldNames.remove();
                params.remove(name);

                continue;
            }

            JsonNode validation = validations.get(name);
            String type = validation.get("type").textValue();
            JsonNode param = params.get(name);

            if (type.equals("array")) {
                if (!param.isArray())
                    throw new MalformedParamException(validation);

                JsonNode rules = validation.get("rules");
                if (rules != null) {
                    if (rules.has("size") && param.size() == rules.get("size").intValue())
                        throw new MalformedParamException(validation);
                    if (rules.has("minSize") && param.size() < rules.get("minSize").intValue())
                        throw new MalformedParamException(validation);
                    if (rules.has("maxSize") && param.size() > rules.get("maxSize").intValue())
                        throw new MalformedParamException(validation);
                }

                JsonNode v = validation.get("validation");

                Iterator<JsonNode> values = param.iterator();
                while (values.hasNext())
                    validation(v, values.next());

                if (v.get("type").textValue().equals("id")) {
                    ArrayNode objectIds = ((ObjectNode) params).putArray(name);
                    values = param.iterator();
                    while (values.hasNext())
                        objectIds.addPOJO(new ObjectId(values.next().textValue()));
                }
            } else if (type.equals("access_token")) {
                if (!param.isTextual())
                    throw new InvalidAccessTokenException();

                String token = param.textValue();
                String regex = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
                if (!token.matches(regex))
                    throw new InvalidAccessTokenException();

                if (validation.get("admin").booleanValue()) {
                    Employee employee = Employee.getByAccessToken(token);
                    if (employee == null)
                        throw new InvalidAccessTokenException();
                } else {
                    User user = User.getByAccessToken(token);
                    if (user == null)
                        throw new InvalidAccessTokenException();

                    int privilege = validation.get("privilege").intValue();
                    if (user.getPrivilege() < privilege)
                        throw new ForbiddenException();
                }
            } else if (type.equals("long")) {
                if (param.isTextual()) {
                    try {
                        long value = Long.parseLong(param.textValue());
                        if (value < 0)
                            throw new MalformedParamException(validation);

                        params.put(name, value);
                    } catch (NumberFormatException e) {
                        throw new MalformedParamException(validation);
                    }
                } else {
                    if (param.isLong() && param.longValue() >= 0)
                        params.put(name, param.longValue());
                    else
                        throw new MalformedParamException(validation);
                }
            } else if (type.equals("date")) {
                if (!param.isTextual())
                    throw new MalformedParamException(validation);

                try {
                    String format = param.textValue().matches("\\d{4}-\\d{1,2}-\\d{1,2}") ?
                        "yyyy-MM-dd" : "yyyy-MM-dd HH:mm";
                    SimpleDateFormat sdf = new SimpleDateFormat(format);
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    params.putPOJO(name, sdf.parse(param.textValue()));
                } catch (ParseException e) {
                    throw new MalformedParamException(validation);
                }
            } else {
                if (type.equals("integer") && param.isTextual()) {
                    try {
                        params.put(name, Integer.parseInt(param.textValue()));
                        param = params.get(name);
                    } catch (NumberFormatException e) {
                        throw new MalformedParamException(validation);
                    }
                } else if (type.equals("boolean") && param.isTextual()) {
                    try {
                        params.put(name, Boolean.parseBoolean(param.textValue()));
                        param = params.get(name);
                    } catch (NumberFormatException e) {
                        throw new MalformedParamException(validation);
                    }
                }

                validation(validation, param);

                if (type.equals("id"))
                    ((ObjectNode) params).putPOJO(name, new ObjectId(param.textValue()));
            }
        }
    }

    private static void validation(JsonNode validation, JsonNode param)
        throws MissingParamException, MalformedParamException, InvalidAccessTokenException,
        ForbiddenException {
        String type = validation.get("type").textValue();
        JsonNode rules = validation.get("rules");

        if (type.equals("id")) {
            if (!param.isTextual() || param.textValue().isEmpty())
                throw new MalformedParamException(validation);

            String regex = "^[0-9a-f]{24}$";
            if (!param.textValue().matches(regex))
                throw new MalformedParamException(validation);
        } else if (type.equals("string")) {
            if (!param.isTextual())
                throw new MalformedParamException(validation);

            if (param.textValue().isEmpty()) {
                if (rules == null || !rules.has("empty"))
                    throw new MalformedParamException(validation);
            }
            else {
                if (rules != null && !validation(rules, param.textValue()))
                    throw new MalformedParamException(validation);
            }
        } else if (type.equals("boolean")) {
            if (!param.isBoolean())
                throw new MalformedParamException(validation);
        } else if (type.equals("integer")) {
            if (!param.isInt())
                throw new MalformedParamException(validation);

            if (rules != null && !validation(rules, param.intValue()))
                throw new MalformedParamException(validation);
        } else if (type.equals("double")) {
            if (!param.isDouble())
                throw new MalformedParamException(validation);
        } else if (type.equals("object")) {
            if (!param.isObject())
                throw new MalformedParamException(validation);

            if (rules != null) {
                if (rules.has("size") && param.size() == rules.get("size").intValue())
                    throw new MalformedParamException(validation);
                if (rules.has("minSize") && param.size() < rules.get("minSize").intValue())
                    throw new MalformedParamException(validation);
                if (rules.has("maxSize") && param.size() > rules.get("maxSize").intValue())
                    throw new MalformedParamException(validation);
            }

            if (rules == null || !rules.has("passUnder"))
                validations(validation.get("validations"), (ObjectNode) param);
        } else {
            throw new MalformedParamException(validation);
        }
    }

    private static boolean validation(JsonNode rules, int value) {
        Iterator<String> iterator = rules.fieldNames();
        while (iterator.hasNext()) {
            String rule = iterator.next();

            if (rule.equals("min")) {
                if (value < rules.get(rule).intValue())
                    return false;
            } else if (rule.equals("max")) {
                if (value > rules.get(rule).intValue())
                    return false;
            } else {
                return false;
            }
        }

        return true;
    }

    private static boolean validation(JsonNode rules, String value) {
        Iterator<String> iterator = rules.fieldNames();
        while (iterator.hasNext()) {
            String rule = iterator.next();

            String regex;
            if (rule.equals("alphaNumeric")) {
                regex = "[A-Za-z0-9]+";
            } else if (rule.equals("email")) {
                regex = "([a-zA-Z0-9._%+-]+)@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}";
            } else if (rule.equals("uuid")) {
                regex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}";
            } else if (rule.equals("url")) {
                try {
                    new URL(value);
                    continue;
                } catch (MalformedURLException e) {
                    return false;
                }
            } else if (rule.matches("^\\(.*\\)$")) {
                regex = "^" + rule + "$";
            } else if (rule.matches("^/.*/$")) {
                regex = rule.replaceFirst("^/", "").replaceFirst("/$", "");
            } else if (rule.equals("length")) {
                if (value.length() == rules.get(rule).intValue())
                    continue;
                else
                    return false;
            } else if (rule.equals("minLength")) {
                if (value.length() >= rules.get(rule).intValue())
                    continue;
                else
                    return false;
            } else if (rule.equals("maxLength")) {
                if (value.length() <= rules.get(rule).intValue())
                    continue;
                else
                    return false;
            } else {
                return false;
            }

            if (!value.matches(regex))
                return false;
        }

        return true;
    }

    private static play.mvc.Result convertResult(Result result) {
        int status = result.getStatus();
        Object content = result.getObject();

        if (content == null) {
            response().setHeader("Access-Control-Allow-Origin", "*");

            return status(status);
        } else if (content instanceof File) {
            return ok((File) content, true).as("image/jpg");
        } else {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStream os = new GZIPOutputStream(baos);
                os.write(content.toString().getBytes());
                os.close();

                response().setHeader("Access-Control-Allow-Origin", "*");
                response().setHeader("Content-Encoding", "gzip");

                return status(status, baos.toByteArray()).as("application/json; charset=utf-8");
            } catch (IOException e) {
                errorlog(e);

                return internalServerError();
            }
        }
    }

    public play.mvc.Result dispatch(String path) {
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

    public static Result webSocketDispath(String json) {
        return webSocketDispath(json, null);
    }

    public static Result webSocketDispath(String json, String session) {
        try {
            ObjectNode params = mapper.readValue(json, ObjectNode.class);

            if (session != null)
                params.put("access_token", session);

            String method = params.has("method") ?
                params.get("method").textValue().toUpperCase() : "POST";

            JsonNode route = match(method, params.get("action").textValue());

            return invoke(route, params);
        } catch (ServiceUnavailableException e) {
            return Error(Error.SERVICE_UNAVAILABLE);
        } catch (MethodNotAllowedException e) {
            return Error(Error.METHOD_NOT_ALLOWED);
        } catch (IOException e) {
            return Error(Error.MALFORMED_JSON);
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
