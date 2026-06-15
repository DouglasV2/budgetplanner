package ai.budgetspace.ai;

/**
 * Sprint 10.10 — result of an LLM call. {@code text} is the raw model output; token counts are
 * nullable because not every provider/path returns usage.
 */
public record LlmCompletion(
        String text,
        Integer inputTokens,
        Integer outputTokens,
        String model
) {
}
