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
        String catalogWarning
) {
}
