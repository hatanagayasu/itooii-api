package controllers;

import controllers.annotations.*;
import controllers.constants.Error;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import org.bson.types.ObjectId;
import com.mongodb.MongoException;

public class DispatchController extends AppController
{
    public static ObjectNode parseValidations(Method method)
    {
        ObjectNode validations = mapper.createObjectNode();

        if (method.getAnnotation(Anonymous.class) == null)
        {
            ObjectNode validation = mapper.createObjectNode();
            validation.put("fullName", "access_token");
            validation.put("type", "access_token");
            validation.put("require", true);

            validations.put("access_token", validation);
        }

        for (Validation v : method.getAnnotationsByType(Validation.class))
        {
            ObjectNode validation = mapper.createObjectNode();
            String name = v.name();
            String type = v.type();
            validation.put("fullName", name);
            validation.put("type", type);
            validation.put("require", v.require());

            if (!v.depend().isEmpty())
            {
                ObjectNode depend = mapper.createObjectNode();
                String[] segs = v.depend().split("=");
                depend.put("name", segs[0]);
                depend.put("value", segs[1]);
                validation.put("depend", depend);
            }

            if (!v.rule().isEmpty())
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

            ObjectNode parent = validations;
            String[] segs = name.split("\\.");
            if (segs.length > 1)
            {
                for (int i = 0; i < segs.length - 1; i++)
                {
                    if (segs[i].endsWith("[]"))
                    {
                        parent = parent.with(segs[i].replace("[]", "")).with("validation");
                        parent = parent.with("validations");
                    }
                    else
                    {
                        parent = parent.with(segs[i]).with("validations");
                    }
                }
            }

            if (name.endsWith("[]"))
                parent.with(name.replace("[]", "")).put("validation", validation);
            else
                parent.put(segs[segs.length - 1], validation);
        }

        return validations;
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

    private static class InvalidAccessTokenException extends Exception
    {
    }

    public static Result invoke(JsonNode route, ObjectNode params)
    {
        try
        {
            Method method = (Method)((POJONode)route.get("method")).getPojo();

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
            Class clazz = e.getCause().getClass();
            if (clazz == MongoException.DuplicateKey.class)
                return Error(Error.MONGO_DUPLICATE_KEY, e.getCause());

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
        catch (InvalidAccessTokenException e)
        {
            return Error(Error.INVALID_ACCESS_TOKEN);
        }
    }

    public static void validations(JsonNode validations, ObjectNode params)
        throws MissingParamException, MalformedParamException, InvalidAccessTokenException
    {
        Iterator<String> fieldNames = validations.fieldNames();
        while (fieldNames.hasNext())
        {
            String name = fieldNames.next();
            JsonNode validation = validations.get(name);

            if (validation.get("require").booleanValue() && !params.has(name))
                throw new MissingParamException(validation);

            if (validation.has("depend"))
            {
                JsonNode depend = validation.get("depend");
                String field = depend.get("name").textValue();
                String value = depend.get("value").textValue();
                if (params.has(field) && value.equals(params.get(field).textValue()))
                {
                    if (!params.has(name))
                        throw new MissingParamException(validation);
                }
                else
                {
                    params.remove(name);
                }
            }
        }

        fieldNames = params.fieldNames();
        while (fieldNames.hasNext())
        {
            String name = fieldNames.next();

            if (!validations.has(name))
            {
                fieldNames.remove();
                params.remove(name);

                continue;
            }

            JsonNode validation = validations.get(name);
            String type = validation.get("type").textValue();
            JsonNode param = params.get(name);

            if (type.equals("array"))
            {
                if (!param.isArray())
                    throw new MalformedParamException(validation);

                JsonNode rules = validation.get("rules");
                if (rules != null)
                {
                    if (rules.has("minSize") && param.size() < rules.get("minSize").intValue())
                        throw new MalformedParamException(validation);

                    if (rules.equals("maxSize") && param.size() > rules.get("maxSize").intValue())
                        throw new MalformedParamException(validation);
                }

                JsonNode v = validation.get("validation");

                Iterator<JsonNode> values = param.iterator();
                while (values.hasNext())
                    validation(v, values.next());

                if (v.get("type").textValue().equals("id"))
                {
                    ArrayNode objectIds = mapper.createArrayNode();
                    values = param.iterator();
                    while (values.hasNext())
                        objectIds.addPOJO(new ObjectId(values.next().textValue()));
                    ((ObjectNode)params).put(name, objectIds);
                }
            }
            else if (type.equals("access_token"))
            {
                if (!param.isTextual())
                    throw new InvalidAccessTokenException();

                String token = param.textValue();
                String regex = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
                if (!token.matches(regex) || !models.User.checkToken(token))
                    throw new InvalidAccessTokenException();
            }
            else
            {
                validation(validation, param);

                if (type.equals("id"))
                    ((ObjectNode)params).putPOJO(name, new ObjectId(param.textValue()));
            }
        }
    }

    private static void validation(JsonNode validation, JsonNode param)
        throws MissingParamException, MalformedParamException, InvalidAccessTokenException
    {
        String type = validation.get("type").textValue();
        JsonNode rules = validation.get("rules");

        if (type.equals("id"))
        {
            if (!param.isTextual() || param.textValue().isEmpty())
                throw new MalformedParamException(validation);

            String regex = "^[0-9a-f]{24}$";
            if (!param.textValue().matches(regex))
                throw new MalformedParamException(validation);
        }
        else if (type.equals("string"))
        {
            if (!param.isTextual() || param.textValue().isEmpty())
                throw new MalformedParamException(validation);

            if (rules != null && !validation(rules, param.textValue()))
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

            if (rules != null && !validation(rules, param.intValue()))
                throw new MalformedParamException(validation);
        }
        else if (type.equals("object"))
        {
            if (!param.isObject())
                throw new MalformedParamException(validation);

            if (rules != null)
            {
                if (rules.has("minSize") && param.size() < rules.get("minSize").intValue())
                {
                    errorlog(param.size());
                    throw new MalformedParamException(validation);
                }

                if (rules.equals("maxSize") && param.size() > rules.get("maxSize").intValue())
                    throw new MalformedParamException(validation);
            }

            if (rules == null || !rules.has("passUnder"))
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
            else if (rule.matches("^(.*)$"))
            {
                regex = "^" + rule + "$";
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
}
