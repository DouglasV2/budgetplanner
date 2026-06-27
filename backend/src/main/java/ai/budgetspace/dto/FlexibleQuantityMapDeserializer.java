package ai.budgetspace.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sprint 10.120: tolerant deserializer for the LLM's {@code quantities} field (category -&gt; count).
 * Per the 10.111 lesson, an LLM-filled field must NEVER crash the whole intent parse (which would
 * silently fall back to rule-based, aiUsed=false). The model is asked for an object like
 * {@code {"dining-chair": 6}} but may instead return an array of {@code {category,count}} objects, or
 * something unexpected. This accepts the object form and the array-of-objects form, ignores anything
 * non-numeric or &le; 0, and returns an empty map for any other shape rather than throwing.
 */
public class FlexibleQuantityMapDeserializer extends JsonDeserializer<Map<String, Integer>> {
    @Override
    public Map<String, Integer> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();
        Map<String, Integer> out = new LinkedHashMap<>();
        if (node == null || node.isNull()) return out;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> putIfValid(out, entry.getKey(), entry.getValue()));
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                if (element != null && element.isObject()) {
                    JsonNode key = firstNonNull(element, "category", "name", "item", "type");
                    JsonNode val = firstNonNull(element, "count", "quantity", "qty", "amount");
                    if (key != null) putIfValid(out, key.asText(null), val);
                }
            }
        }
        return out;
    }

    private static JsonNode firstNonNull(JsonNode obj, String... keys) {
        for (String key : keys) {
            JsonNode v = obj.get(key);
            if (v != null && !v.isNull()) return v;
        }
        return null;
    }

    private static void putIfValid(Map<String, Integer> out, String key, JsonNode valueNode) {
        if (key == null || key.isBlank() || valueNode == null || valueNode.isNull()) return;
        int value;
        if (valueNode.isInt() || valueNode.isLong()) {
            value = valueNode.asInt();
        } else {
            // tolerate "6" or "6 kom" style strings
            String text = valueNode.asText("").replaceAll("[^0-9]", "");
            if (text.isBlank()) return;
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return;
            }
        }
        if (value > 0) out.put(key.trim(), value);
    }
}
