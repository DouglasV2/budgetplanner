package ai.budgetspace.dto;

public record ReplaceProductRequest(FurnishingPlanDto plan, PlannerInputDto input, String productId, String changeType) {
}
