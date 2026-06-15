package ai.budgetspace.ai;

/**
 * Sprint 10.10 — a single LLM call: a system instruction, the user content, an output-token cap and
 * the use case (for usage tracking). {@code expectJson} asks the provider for JSON-only output where
 * the provider supports a JSON mode.
 */
public record LlmCompletionRequest(
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        boolean expectJson,
        String useCase
) {
}
