package ai.budgetspace.dto;

import java.math.BigDecimal;
import java.util.List;

public record FurnishingPlanDto(
        String id,
        String name,
        String label,
        String description,
        List<PlanItemDto> items,
        BigDecimal total,
        BigDecimal savings,
        int fitScore,
        String shoppingEffort,
        int styleConsistency,
        List<String> retailersUsed
) {
}
