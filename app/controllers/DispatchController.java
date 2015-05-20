package controllers;

import controllers.constants.Error;
import controllers.exceptions.InvalidSigningException;

import models.User;

import java.lang.NumberFormatException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import org.bson.types.ObjectId;
import com.mongodb.DuplicateKeyException;

public class DispatchController extends AppController {
    public static final Pattern parenthesesPattern = Pattern.compile("\\(\\s*(.+)\\s*\\)"),
        pairPattern = Pattern.compile("([^\\s=,]+)\\s*=\\s*(\"[^\"]+\"|[^\\s,\"]+)");

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

    public static void parseVlidation(String line, int no, ObjectNode validations) {
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
        public static final long serialVersionUID = -1;
    }

    private static class ForbiddenException extends Exception {
        public static final long serialVersionUID = -1;
    }

    public static Result invoke(JsonNode route, ObjectNode params) {
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

    public static void validations(JsonNode validations, ObjectNode params)
        throws MissingParamException, MalformedParamException, InvalidAccessTokenException,
        ForbiddenException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

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

                User user = User.getByToken(token);
                if (user == null)
                    throw new InvalidAccessTokenException();

                int privilege = validation.get("privilege").intValue();
                if (user.getPrivilege() < privilege)
                    throw new ForbiddenException();
            } else if (type.equals("epoch")) {
                if (param.isTextual()) {
                    try {
                        long epoch = Long.parseLong(param.textValue());
                        if (epoch < 0)
                            throw new MalformedParamException(validation);

                        params.put(name, epoch);
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
                regex = "([a-z0-9._%+-]+)@[a-z0-9.-]+\\.[a-z]{2,4}";
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
}
