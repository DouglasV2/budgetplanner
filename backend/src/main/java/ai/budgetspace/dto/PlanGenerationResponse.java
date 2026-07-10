package ai.budgetspace.dto;

import java.util.List;

/**
 * Plan generation result. Sprint 9.6 adds a partial-plan signal: when the catalog cannot
 * supply a required category for the room, {@code partialPlan} is true and
 * {@code catalogWarning} carries a short human message the UI can show. Older clients that
 * ignore the new fields keep working.
 */
public record PlanGenerationResponse(
        PlannerInputDto input,
        List<FurnishingPlanDto> plans,
        boolean partialPlan,
        List<String> missingImportantCategories,
        String catalogWarning,
        // Sprint 10.10: how the prompt was understood (AI or rule-based). Null on older call paths.
        PlannerIntentAnalysisDto intentAnalysis,
        // Sprint 10.51: matched second-hand ("Rabljeno") listings for the request — a separate, optional
        // block the UI shows under the new-retail plans. Shared across all three plan tiers (it is tied to
        // the room, not the budget tier) and NEVER counted into any plan total. Empty when no marketplace
        // feed is configured or nothing matches. See docs/marketplace-sourcing.md §5.
        List<ProductDto> secondHandSuggestions,
        // Sprint 10.175 (kitchen Increment 1): the "complete kitchen" section — real modular sets + parsed
        // understanding + the honest modular-note flag. Null on every non-complete-kitchen request.
        CompleteKitchenDto completeKitchen
) {
    /** Null-safe: second-hand suggestions are always a list, never null, for the frontend. */
    public PlanGenerationResponse {
        secondHandSuggestions = secondHandSuggestions == null ? List.of() : secondHandSuggestions;
    }

    /** Backwards-compatible constructor (pre-10.10) — no intent analysis, no second-hand block. */
    public PlanGenerationResponse(PlannerInputDto input, List<FurnishingPlanDto> plans, boolean partialPlan,
                                  List<String> missingImportantCategories, String catalogWarning) {
        this(input, plans, partialPlan, missingImportantCategories, catalogWarning, null, List.of(), null);
    }

    /** Sprint 10.51 — built with second-hand suggestions but before the prompt analysis is attached. */
    public PlanGenerationResponse(PlannerInputDto input, List<FurnishingPlanDto> plans, boolean partialPlan,
                                  List<String> missingImportantCategories, String catalogWarning,
                                  List<ProductDto> secondHandSuggestions) {
        this(input, plans, partialPlan, missingImportantCategories, catalogWarning, null, secondHandSuggestions, null);
    }

    public PlanGenerationResponse withAnalysis(PlannerIntentAnalysisDto analysis) {
        return new PlanGenerationResponse(input, plans, partialPlan, missingImportantCategories, catalogWarning,
                analysis, secondHandSuggestions, completeKitchen);
    }

    /** Sprint 10.175: attach the complete-kitchen section (preserving everything else). */
    public PlanGenerationResponse withCompleteKitchen(CompleteKitchenDto ck) {
        return new PlanGenerationResponse(input, plans, partialPlan, missingImportantCategories, catalogWarning,
                intentAnalysis, secondHandSuggestions, ck);
    }
}
