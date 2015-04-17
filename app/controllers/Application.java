package controllers;

import play.Play;

import controllers.annotations.*;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;

public class Application extends AppController {
    private static JsonNode countries;
    private static JsonNode cities;
    private static JsonNode languages;
    private static JsonNode locales;

    static {
        init();
    }

    private static void init() {
        try {
            String path = Play.application().path() + "/conf/";

            countries = mapper.readValue(new File(path + "country.json"), JsonNode.class);
            cities = mapper.readValue(new File(path + "city.json"), JsonNode.class);
            languages = mapper.readValue(new File(path + "language.json"), JsonNode.class);
            locales = mapper.readValue(new File(path + "locale.json"), JsonNode.class);
        } catch (IOException e) {
            errorlog(e);
        }
    }

    @Anonymous
    public static Result getCountry(JsonNode params) {
        return Ok(countries);
    }

    @Anonymous
    @Validation(name = "id", rule = "length=2", require = true)
    public static Result getCity(JsonNode params) {
        String id = params.get("id").textValue();

        if (!cities.has(id))
            return NotFound();

        return Ok(cities.get(id));
    }

    @Anonymous
    public static Result getLanguage(JsonNode params) {
        return Ok(languages);
    }

    @Anonymous
    public static Result getLocale(JsonNode params) {
        return Ok(locales);
    }
}
