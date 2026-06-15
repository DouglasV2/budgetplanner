package ai.budgetspace.ai;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 10.10 — OpenAI Chat Completions client (raw HTTP via the JDK client). Uses JSON mode
 * ({@code response_format: json_object}) for structured intent extraction; the caller validates the
 * JSON against the DTO and falls back on any problem. The API key comes from {@link LlmProperties}
 * (backend-only) and is sent as a Bearer token — never logged.
 */
@Component
public class OpenAiLlmClient implements LlmClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmProperties properties;
    private final String baseUrl;

    public OpenAiLlmClient(LlmProperties properties,
                           @Value("${budgetspace.ai.openai-base-url:https://api.openai.com}") String baseUrl) {
        this.properties = properties;
        this.baseUrl = baseUrl;
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.OPENAI;
    }

    @Override
    public LlmCompletion complete(LlmCompletionRequest request) throws IOException, InterruptedException {
        String apiKey = properties.apiKeyFor(LlmProvider.OPENAI);
        if (apiKey.isBlank()) throw new IOException("OpenAI API key not configured");
        String model = properties.resolvedModel(LlmProvider.OPENAI);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        // max_completion_tokens is accepted by current OpenAI models (incl. the 4o-mini default).
        body.put("max_completion_tokens", Math.max(1, Math.min(request.maxOutputTokens(), properties.maxOutputTokens())));
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        messages.add(Map.of("role", "user", "content", request.userPrompt()));
        body.put("messages", messages);
        if (request.expectJson()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .timeout(properties.timeout())
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("OpenAI API status " + response.statusCode());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode choice = root.path("choices").path(0).path("message").path("content");
        String text = choice.isMissingNode() ? "" : choice.asText("").trim();
        if (text.isEmpty()) {
            throw new IOException("empty OpenAI response");
        }
        Integer in = root.path("usage").has("prompt_tokens") ? root.path("usage").path("prompt_tokens").asInt() : null;
        Integer out = root.path("usage").has("completion_tokens") ? root.path("usage").path("completion_tokens").asInt() : null;
        return new LlmCompletion(text, in, out, model);
    }
}
