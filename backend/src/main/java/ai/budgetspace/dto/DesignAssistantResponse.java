package ai.budgetspace.dto;

import java.util.List;

/**
 * Sprint 10.8 — output of the (currently rule-based) design assistant. {@code summary} is a short
 * paragraph describing the generated plan; {@code highlights} are a few one-line takeaways the UI
 * can render as bullets. Kept small and additive so a later LLM-backed version can fill the same
 * shape without changing the API or the frontend.
 */
public record DesignAssistantResponse(
        String summary,
        List<String> highlights
) {
}
