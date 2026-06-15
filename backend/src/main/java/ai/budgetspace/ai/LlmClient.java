package ai.budgetspace.ai;

/**
 * Sprint 10.10 — provider-agnostic LLM client. One implementation per provider
 * ({@code AnthropicLlmClient}, {@code OpenAiLlmClient}); the active one is chosen at runtime by
 * {@link LlmClientFactory} from configuration. Callers never depend on a concrete provider.
 */
public interface LlmClient {

    LlmProvider provider();

    /**
     * Runs one completion. Implementations throw on any transport/HTTP/parse problem so the caller
     * can fall back to the deterministic rule-based path — they never return a fabricated result.
     */
    LlmCompletion complete(LlmCompletionRequest request) throws Exception;
}
