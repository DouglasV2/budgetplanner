package ai.budgetspace.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sprint 10.10 — all AI configuration, sourced from environment variables. API keys are read here
 * (backend only) and are never logged, returned to the frontend, or written to any response.
 *
 * <p>Defaults are deliberately safe: AI is <b>off</b>, there is no key, the model is a cheap "mini"
 * model, the timeout is short and the output cap is small.</p>
 */
@Component
public class LlmProperties {
    private static final String OPENAI_DEFAULT_MODEL = "gpt-4o-mini";
    private static final String ANTHROPIC_DEFAULT_MODEL = "claude-haiku-4-5";
    // Sprint 10.66/10.68: a cheap, fast Flash model — enough to structure a prompt + write a short rationale.
    // (gemini-2.0-flash 404s on current keys; gemini-2.5-flash is the available stable Flash model.)
    private static final String GEMINI_DEFAULT_MODEL = "gemini-2.5-flash";

    private final boolean enabled;
    private final LlmProvider provider;
    private final String openAiApiKey;
    private final String anthropicApiKey;
    private final String geminiApiKey;
    private final String model;
    private final int timeoutSeconds;
    private final int maxOutputTokens;

    public LlmProperties(
            @Value("${budgetspace.ai.enabled:false}") boolean enabled,
            @Value("${budgetspace.ai.provider:off}") String provider,
            @Value("${budgetspace.ai.openai-api-key:${OPENAI_API_KEY:}}") String openAiApiKey,
            @Value("${budgetspace.ai.anthropic-api-key:${ANTHROPIC_API_KEY:}}") String anthropicApiKey,
            @Value("${budgetspace.ai.gemini-api-key:${GEMINI_API_KEY:}}") String geminiApiKey,
            @Value("${budgetspace.ai.model:}") String model,
            @Value("${budgetspace.ai.timeout-seconds:15}") int timeoutSeconds,
            @Value("${budgetspace.ai.max-output-tokens:700}") int maxOutputTokens) {
        this.enabled = enabled;
        this.provider = LlmProvider.from(provider);
        this.openAiApiKey = trim(openAiApiKey);
        this.anthropicApiKey = trim(anthropicApiKey);
        this.geminiApiKey = trim(geminiApiKey);
        this.model = trim(model);
        this.timeoutSeconds = timeoutSeconds <= 0 ? 15 : timeoutSeconds;
        this.maxOutputTokens = maxOutputTokens <= 0 ? 700 : maxOutputTokens;
    }

    public boolean enabled() {
        return enabled;
    }

    public LlmProvider provider() {
        return provider;
    }

    public String apiKeyFor(LlmProvider target) {
        return switch (target) {
            case OPENAI -> openAiApiKey;
            case ANTHROPIC -> anthropicApiKey;
            case GEMINI -> geminiApiKey;
            case OFF -> "";
        };
    }

    /** The configured model, or the cheap provider default when unset. */
    public String resolvedModel(LlmProvider target) {
        if (model != null && !model.isBlank()) return model;
        return switch (target) {
            case OPENAI -> OPENAI_DEFAULT_MODEL;
            case ANTHROPIC -> ANTHROPIC_DEFAULT_MODEL;
            case GEMINI -> GEMINI_DEFAULT_MODEL;
            case OFF -> "";
        };
    }

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    /** True when AI is enabled, a real provider is selected, and that provider has an API key. */
    public boolean aiUsable() {
        return enabled && provider != LlmProvider.OFF && !apiKeyFor(provider).isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
