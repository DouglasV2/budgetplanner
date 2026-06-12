package ai.budgetspace.dto;

import java.math.BigDecimal;
import java.util.List;

public record FurnishingPlanDto(
        String id,
        String name,
        String label,
        String description,
        String summary,
        String goodFor,
        String tradeoff,
        String budgetStatus,
        String advisorNote,
        String nextStep,
        List<String> savingTips,
        List<String> upgradeTips,
        List<PlanItemDto> items,
        BigDecimal total,
        BigDecimal savings,
        int fitScore,
        String shoppingEffort,
        int styleConsistency,
        List<String> retailersUsed
) {
}
