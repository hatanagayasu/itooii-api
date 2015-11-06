package models;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public class CustomJsonDeserializer extends JsonDeserializer<JsonNode> {
    public JsonNode deserialize(JsonParser parser, DeserializationContext context)
        throws IOException, JsonProcessingException {
            return parser.readValueAsTree();
    }
}
