package ai.budgetspace.ai;

import java.util.Locale;

/**
 * Sprint 10.10 — which LLM backend the app uses for prompt understanding.
 *
 * <p>{@link #OFF} means no model is called at all; the deterministic rule-based parser
 * ({@code PlannerIntentExtractor}) is used. The app defaults to {@code OFF} so it runs with zero
 * cost and no API key until an operator explicitly turns AI on.</p>
 */
public enum LlmProvider {
    OFF,
    ANTHROPIC,
    OPENAI,
    // Sprint 10.66: Google Gemini — the cheapest option (and a free tier), chosen as the default AI provider
    // for the cost-sensitive early stage. The task (structuring a prompt + a short rationale) only needs a Flash model.
    GEMINI;

    public static LlmProvider from(String value) {
        if (value == null || value.isBlank()) return OFF;
        try {
            return LlmProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return OFF;
        }
    }
}
