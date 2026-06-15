package ai.budgetspace.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Sprint 10.10 — structured understanding of a user's free-text prompt, produced either by the LLM
 * (prompt intelligence) or by the deterministic rule-based parser. This is the contract the LLM must
 * fill; it is then mapped onto {@link PlannerInputDto} and the deterministic planner picks the real
 * products. The LLM never returns products, prices or URLs — only planning parameters.
 *
 * <p>{@code aiUsed}/{@code source}/{@code model} are filled in by the backend, not the model.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlannerIntentAnalysisDto(
        String roomType,
        Integer budget,
        String currency,
        Integer roomSize,
        String style,
        List<String> preferredRetailers,
        List<String> mustHaveCategories,
        List<String> alreadyHaveCategories,
        List<String> avoidCategories,
        List<String> colorPreferences,
        List<String> materialPreferences,
        String qualityPreference,
        String urgency,
        Double confidence,
        List<String> missingImportantInfo,
        String userGoalSummary,
        String normalizedPrompt,
        List<String> warnings,
        boolean aiUsed,
        String source
) {
    private static <T> List<T> orEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }

    /** Returns a copy with metadata filled and all nullable collections/confidence normalised. */
    public PlannerIntentAnalysisDto withMeta(boolean aiUsed, String source, String rawPrompt) {
        double safeConfidence = confidence == null ? 0.0 : Math.max(0.0, Math.min(1.0, confidence));
        String prompt = normalizedPrompt == null || normalizedPrompt.isBlank() ? rawPrompt : normalizedPrompt;
        return new PlannerIntentAnalysisDto(
                roomType, budget, currency == null ? "EUR" : currency, roomSize, style,
                orEmpty(preferredRetailers), orEmpty(mustHaveCategories), orEmpty(alreadyHaveCategories),
                orEmpty(avoidCategories), orEmpty(colorPreferences), orEmpty(materialPreferences),
                qualityPreference, urgency, safeConfidence, orEmpty(missingImportantInfo),
                userGoalSummary, prompt, orEmpty(warnings), aiUsed, source);
    }

    /** Low confidence → the UI should tell the user we're unsure rather than acting certain. */
    public boolean isLowConfidence() {
        return confidence != null && confidence < 0.5;
    }
}
