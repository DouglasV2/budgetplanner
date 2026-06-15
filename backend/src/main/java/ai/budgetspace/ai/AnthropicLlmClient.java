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
import java.util.List;
import java.util.Map;

/**
 * Sprint 10.10 — Anthropic Messages API client (raw HTTP via the JDK client, so AI stays an
 * optional feature with no extra build dependency). The API key comes from {@link LlmProperties}
 * (backend-only) and is sent in the {@code x-api-key} header — never logged.
 */
@Component
public class AnthropicLlmClient implements LlmClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final LlmProperties properties;
    private final String baseUrl;

    public AnthropicLlmClient(LlmProperties properties,
                              @Value("${budgetspace.ai.anthropic-base-url:https://api.anthropic.com}") String baseUrl) {
        this.properties = properties;
        this.baseUrl = baseUrl;
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.ANTHROPIC;
    }

    @Override
    public LlmCompletion complete(LlmCompletionRequest request) throws IOException, InterruptedException {
        String apiKey = properties.apiKeyFor(LlmProvider.ANTHROPIC);
        if (apiKey.isBlank()) throw new IOException("Anthropic API key not configured");
        String model = properties.resolvedModel(LlmProvider.ANTHROPIC);

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", Math.max(1, Math.min(request.maxOutputTokens(), properties.maxOutputTokens())),
                "system", request.systemPrompt(),
                "messages", List.of(Map.of("role", "user", "content", request.userPrompt())));

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .timeout(properties.timeout())
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Anthropic API status " + response.statusCode());
        }

        JsonNode root = MAPPER.readTree(response.body());
        if ("refusal".equals(root.path("stop_reason").asText(""))) {
            throw new IOException("Anthropic refused the request");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText());
        }
        Integer in = root.path("usage").has("input_tokens") ? root.path("usage").path("input_tokens").asInt() : null;
        Integer out = root.path("usage").has("output_tokens") ? root.path("usage").path("output_tokens").asInt() : null;
        return new LlmCompletion(text.toString().trim(), in, out, model);
    }
}
