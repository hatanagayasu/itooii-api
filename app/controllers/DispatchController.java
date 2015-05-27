package controllers;

import controllers.constants.Error;
import controllers.exceptions.InvalidSigningException;
import controllers.exceptions.ObjectForbiddenException;

import models.User;

import java.lang.NumberFormatException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import org.bson.types.ObjectId;
import com.mongodb.DuplicateKeyException;

public class DispatchController extends AppController {
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
                regex = "([a-zA-Z0-9._%+-]+)@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}";
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
