package ai.budgetspace.dto;

import java.time.Instant;

public record SavedPlanResponse(String id, FurnishingPlanDto plan, PlannerInputDto input, Instant createdAt) {
}
