package ai.budgetspace.dto;

import java.util.List;

public record PlanGenerationResponse(PlannerInputDto input, List<FurnishingPlanDto> plans) {
}
