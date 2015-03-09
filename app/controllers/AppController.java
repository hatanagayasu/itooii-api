package controllers;

import play.*;
import play.mvc.*;
import controllers.constants.Error;
import controllers.pair.*;
import models.Model;
import models.User;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;

import org.bson.types.ObjectId;

public class AppController extends Controller
{
    public static final ObjectMapper mapper = new ObjectMapper();
	private static ConcurrentHashMap<ObjectId, UserTable> UsrTabMap= new ConcurrentHashMap<ObjectId, UserTable>();
	private static ArrayBlockingQueue<ObjectId> InPairQueue = new ArrayBlockingQueue<ObjectId>(1000);
	private static ArrayBlockingQueue<PairedTalkData> OutPairQueue = new ArrayBlockingQueue<PairedTalkData>(1000);

    static
    {
    	    new Thread(new CalcMatchScore(UsrTabMap, InPairQueue)).start();
    	    new Thread(new PairResult(UsrTabMap, OutPairQueue)).start();
    	    new Thread(new SendPairedUsers(OutPairQueue)).start();
    }
    
    public static void inPairQueue(ObjectId id)
    {
    		try
    		{
    	        InPairQueue.put(id);
    		}
    		catch (Exception e)
    		{
   
    		}
    }
    
    public static Result Ok()
    {
        return new Result(200);
    }

    public static Result Ok(Model model)
    {
        return new Result(200, Model.toJson(model));
    }

    public static Result Ok(List<? extends Model> models)
    {
        return new Result(200, Model.toJson(models));
    }

    public static Result Ok(JsonNode node)
    {
        return new Result(200, node);
    }

    public static Result Ok(ObjectNode node)
    {
        return new Result(200, node);
    }

    public static Result Error(Error error)
    {
        ObjectNode result = mapper.createObjectNode();
        result.put("error_code", error.getCode());
        result.put("description", error.getDescription());

        return new Result(error.getCode() / 100, result);
    }

    public static Result Error(Error error, Object... params)
    {
        ObjectNode result = mapper.createObjectNode();
        result.put("error_code", error.getCode());
        result.put("description", String.format(error.getDescription(), params));

        return new Result(error.getCode() / 100, result);
    }

    public static Result NotFound()
    {
        return new Result(404);
    }

    public static void errorlog(Object error)
    {
        Logger.error(error.toString());
    }

    public static void errorlog(Throwable cause)
    {
        StringWriter errors = new StringWriter();
        cause.printStackTrace(new PrintWriter(errors));
        Logger.error(errors.toString());
    }

    public static User getMe(JsonNode params)
    {
        String token = params.get("access_token").textValue();

        return User.getByToken(token);
    }

    public static ObjectId getObjectId(JsonNode params, String name)
    {
        return (ObjectId)((POJONode)params.get(name)).getPojo();
    }

    public static void sendEvent(ObjectId userId, String token, JsonNode event)
    {
        if (WebSocketController.webSocketMap.containsKey(token))
        {
            WebSocket.Out<String> out = WebSocketController.webSocketMap.get(token);
            out.write(event.toString());
        }
        else
        {
            User.offline(userId.toString(), token);
        }
    }
    
    public static void sendEvent(ObjectId userId, JsonNode event)
    {
        Map<String,String> hosts = User.getTokenHosts(userId);

        for (String token : hosts.keySet())
        {
            String host = hosts.get(token);
            if (host.equals(WebSocketController.host))
            {
                sendEvent(userId, token, event);
            }
            else
            {
                //TODO
            }
        }
    }
}
