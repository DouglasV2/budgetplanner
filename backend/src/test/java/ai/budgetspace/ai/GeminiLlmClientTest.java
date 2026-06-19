package ai.budgetspace.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10.66 — the Gemini client's request/response shape differs from OpenAI's (top-level systemInstruction,
 * contents, JSON via responseMimeType, candidates/usageMetadata). These tests pin that mapping without a network.
 */
class GeminiLlmClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildsSystemInstructionContentsAndJsonMode() throws Exception {
        LlmCompletionRequest request = new LlmCompletionRequest("be precise", "imam 1500 EUR", 300, true, "intent");

        JsonNode body = MAPPER.readTree(GeminiLlmClient.buildBody(request, 250));

        assertThat(body.path("systemInstruction").path("parts").path(0).path("text").asText()).isEqualTo("be precise");
        assertThat(body.path("contents").path(0).path("role").asText()).isEqualTo("user");
        assertThat(body.path("contents").path(0).path("parts").path(0).path("text").asText()).isEqualTo("imam 1500 EUR");
        assertThat(body.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(250);
        assertThat(body.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
    }

    @Test
    void omitsJsonModeWhenNotExpected() throws Exception {
        LlmCompletionRequest request = new LlmCompletionRequest("sys", "user", 200, false, "summary");

        JsonNode body = MAPPER.readTree(GeminiLlmClient.buildBody(request, 200));

        assertThat(body.path("generationConfig").has("responseMimeType")).isFalse();
    }

    @Test
    void parsesTextAndUsageFromAGeminiResponse() throws Exception {
        String responseBody = """
                {
                  "candidates": [
                    {"content": {"parts": [{"text": "{\\"room\\":"}, {"text": "\\"living-room\\"}"}]}}
                  ],
                  "usageMetadata": {"promptTokenCount": 42, "candidatesTokenCount": 17}
                }
                """;

        LlmCompletion completion = GeminiLlmClient.parseCompletion(responseBody, "gemini-2.0-flash");

        // The two text parts are concatenated into one payload.
        assertThat(completion.text()).isEqualTo("{\"room\":\"living-room\"}");
        assertThat(completion.inputTokens()).isEqualTo(42);
        assertThat(completion.outputTokens()).isEqualTo(17);
        assertThat(completion.model()).isEqualTo("gemini-2.0-flash");
    }

    @Test
    void throwsOnAnEmptyResponse() {
        assertThatThrownBy(() -> GeminiLlmClient.parseCompletion("{\"candidates\":[]}", "gemini-2.0-flash"))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void reportsItselfAsTheGeminiProvider() {
        LlmProperties properties = new LlmProperties(true, "gemini", "", "", "test-key", "", 15, 700);
        GeminiLlmClient client = new GeminiLlmClient(properties, "https://example.test");
        assertThat(client.provider()).isEqualTo(LlmProvider.GEMINI);
    }
}
