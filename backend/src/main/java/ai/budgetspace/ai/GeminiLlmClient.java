package ai.budgetspace.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 10.66 — Google Gemini client (Generative Language API, {@code generateContent}) over raw JDK HTTP, no
 * SDK (so the off-by-default feature can never break the corporate-mirror build, like the OpenAI/Anthropic
 * clients). Gemini is the cheapest provider and has a free tier, so it is the default for the early stage.
 *
 * <p>Gemini's shape differs from OpenAI: the system prompt is a top-level {@code systemInstruction}, messages are
 * {@code contents}, and JSON mode is {@code generationConfig.responseMimeType=application/json}. The API key is
 * sent in the {@code x-goog-api-key} header (never the URL, so it can't land in logs/history) and comes from
 * {@link LlmProperties} (backend-only) — never logged or returned to the client.</p>
 */
@Component
public class GeminiLlmClient implements LlmClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmProperties properties;
    private final String baseUrl;

    public GeminiLlmClient(LlmProperties properties,
                           @Value("${budgetspace.ai.gemini-base-url:https://generativelanguage.googleapis.com}") String baseUrl) {
        this.properties = properties;
        this.baseUrl = baseUrl;
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.GEMINI;
    }

    @Override
    public LlmCompletion complete(LlmCompletionRequest request) throws IOException, InterruptedException {
        String apiKey = properties.apiKeyFor(LlmProvider.GEMINI);
        if (apiKey.isBlank()) throw new IOException("Gemini API key not configured");
        String model = properties.resolvedModel(LlmProvider.GEMINI);
        int maxTokens = Math.max(1, Math.min(request.maxOutputTokens(), properties.maxOutputTokens()));

        HttpRequest httpRequest = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
                .header("x-goog-api-key", apiKey)
                .header("content-type", "application/json")
                .timeout(properties.timeout())
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(request, maxTokens), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Gemini API status " + response.statusCode());
        }
        return parseCompletion(response.body(), model);
    }

    /** Builds the Gemini request JSON. Package-private so the body shape is unit-tested without a network call. */
    static String buildBody(LlmCompletionRequest request, int maxTokens) throws JsonProcessingException {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", maxTokens);
        if (request.expectJson()) {
            // Gemini's controlled-generation JSON mode, mirroring OpenAI's response_format=json_object.
            generationConfig.put("responseMimeType", "application/json");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.systemPrompt()))));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", request.userPrompt())))));
        body.put("generationConfig", generationConfig);
        return MAPPER.writeValueAsString(body);
    }

    /** Parses a Gemini {@code generateContent} response into an {@link LlmCompletion}. Package-private for tests. */
    static LlmCompletion parseCompletion(String responseBody, String model) throws IOException {
        JsonNode root = MAPPER.readTree(responseBody == null || responseBody.isBlank() ? "{}" : responseBody);
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                String chunk = part.path("text").asText("");
                if (!chunk.isEmpty()) text.append(chunk);
            }
        }
        String content = text.toString().trim();
        if (content.isEmpty()) {
            throw new IOException("empty Gemini response");
        }
        JsonNode usage = root.path("usageMetadata");
        Integer in = usage.has("promptTokenCount") ? usage.path("promptTokenCount").asInt() : null;
        Integer out = usage.has("candidatesTokenCount") ? usage.path("candidatesTokenCount").asInt() : null;
        return new LlmCompletion(content, in, out, model);
    }
}
