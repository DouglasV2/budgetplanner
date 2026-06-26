package ai.budgetspace.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Sprint 10.111: the LLM sometimes returns a scalar field as an ARRAY — e.g. a multi-style prompt
 * ("toplo i moderno") comes back as {@code "style": ["warm","modern"]}. Jackson then can't bind it to a
 * {@code String} and the WHOLE intent parse throws, so every such request silently falls back to the
 * rule-based parser (aiUsed=false). This deserializer accepts either a plain string or an array (taking the
 * first non-blank element), so a multi-valued answer no longer breaks the AI path.
 */
public class FlexibleStringDeserializer extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (element != null && !element.isNull()) {
                    String value = element.asText(null);
                    if (value != null && !value.isBlank()) return value;
                }
            }
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
