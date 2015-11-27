package controllers;

import controllers.constants.Error;

import models.Event;
import models.User;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import org.bson.types.ObjectId;

public class EventController extends AppController {
    public static Result gets(JsonNode params) {
        User me = getMe(params);
        long until = params.has("until") ? params.get("until").longValue() : Long.MAX_VALUE;
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(Event.gets(me.getId(), new Date(until), limit));
    }

    public static Result getHosting(JsonNode params) {
        User me = getMe(params);
        long until = params.has("until") ? params.get("until").longValue() : Long.MAX_VALUE;
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(Event.getHosting(me.getId(), new Date(until), limit));
    }

    public static Result search(JsonNode params) {
        long until = params.has("until") ? params.get("until").longValue() : Long.MAX_VALUE;
        int limit = params.has("limit") ? params.get("limit").intValue() : 25;

        return Ok(Event.search(new Date(until), limit));
    }

    public static Result get(JsonNode params) {
        ObjectId eventId = getObjectId(params, "event_id");

        Event event = Event.get(eventId);
        if (event == null || event.getDeleted() != null)
            return NotFound();

        return Ok(event);
    }

    public static Result getByAlias(JsonNode params) {
        String alias = params.get("alias").textValue();

        Event event = Event.getByAlias(alias);
        if (event == null || event.getDeleted() != null)
            return NotFound();

        return Ok(event);
    }

    public static Result add(JsonNode params) {
        User me = getMe(params);
        String name = params.get("name").textValue();
        String details = params.has("details") ? params.get("details").textValue() : null;
        int lang0 = params.get("lang0").intValue();
        int lang1 = params.has("lang1") ? params.get("lang1").intValue() : 0;
        Date from = getDate(params, "from");
        Date to = getDate(params, "to");

        Event event = new Event(me.getId(), name, details, lang0, lang1, from, to);
        event.save(me);

        return Ok(event);
    }

    public static Result update(JsonNode params) {
        User me = getMe(params);
        ObjectId eventId = getObjectId(params, "event_id");

        Event event = Event.get(eventId);
        if (event == null || event.getDeleted() != null)
            return NotFound();

        if (!event.getUserId().equals(me.getId()))
            return ObjectForbidden();

        event.update(params);

        return Ok();
    }

    public static Result join(JsonNode params) {
        User me = getMe(params);
        ObjectId eventId = getObjectId(params, "event_id");

        Event event = Event.get(eventId);
        if (event == null || event.getDeleted() != null)
            return NotFound();

        if (!event.getMembers().contains(me.getId()))
            event.join(me);

        return Ok();
    }

    public static Result leave(JsonNode params) {
        User me = getMe(params);
        ObjectId eventId = getObjectId(params, "event_id");

        Event event = Event.get(eventId);
        if (event == null || event.getDeleted() != null)
            return NotFound();

        if (event.getUserId().equals(me.getId()))
            return Error(Error.SELF_FORBIDDEN);

        if (event.getMembers().contains(me.getId()))
            event.leave(me.getId());

        return Ok();
    }

    public static Result enter(JsonNode params) {
        User me = getMe(params);
        String token = params.get("access_token").textValue();
        ObjectId eventId = getObjectId(params, "event_id");

        Event event = Event.get(eventId);
        if (event == null || event.getDeleted() != null)
            return NotFound();

        event.enter(me == null ? null : me.getId(), token);

        return Ok();
    }

    public static Result exit(JsonNode params) {
        User me = getMe(params);
        String token = params.get("access_token").textValue();
        ObjectId eventId = getObjectId(params, "event_id");

        Event event = Event.get(eventId);
        if (event == null || event.getDeleted() != null)
            return NotFound();

        event.exit(me == null ? null : me.getId(), token);

        return Ok();
    }

    public static Result getOnlineUser(JsonNode params) {
        ObjectId eventId = getObjectId(params, "event_id");
        long until = params.has("until") ? params.get("until").longValue() : now();
        int limit = params.has("limit") ? params.get("limit").intValue() : 50;

        Event event = Event.get(eventId);
        if (event == null || event.getDeleted() != null)
            return NotFound();

        return Ok(event.getOnlineUser(eventId, until, limit));
    }

    public static Result delete(JsonNode params) {
        User me = getMe(params);
        ObjectId eventId = getObjectId(params, "event_id");

        Event event = Event.get(eventId);
        if (event == null || event.getDeleted() != null)
            return NotFound();

        if (!event.getUserId().equals(me.getId()))
            return ObjectForbidden();

        event.delete();

        return Ok();
    }
}
