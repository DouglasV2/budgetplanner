package ai.budgetspace.dto;

import java.util.List;

public record PlannerInputDto(
        String prompt,
        int budget,
        String roomType,
        String style,
        String location,
        int size,
        String retailerMode,
        List<String> selectedRetailers,
        String optimizationGoal,
        String furnishingLevel,
        List<String> mustHaveCategories,
        List<String> alreadyHaveCategories,
        List<String> lockedProductIds
) {
    public PlannerInputDto normalized() {
        return new PlannerInputDto(
                prompt == null ? "" : prompt,
                budget <= 0 ? 1500 : budget,
                blank(roomType) ? "living-room" : roomType,
                blank(style) ? "bright" : style,
                blank(location) ? "Zagreb" : location,
                size <= 0 ? 20 : size,
                blank(retailerMode) ? "multi" : retailerMode,
                selectedRetailers == null ? List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina") : selectedRetailers,
                blank(optimizationGoal) ? "best-value" : optimizationGoal,
                blank(furnishingLevel) ? "comfort" : furnishingLevel,
                mustHaveCategories == null ? List.of() : mustHaveCategories,
                alreadyHaveCategories == null ? List.of() : alreadyHaveCategories,
                lockedProductIds == null ? List.of() : lockedProductIds
        );
    }

    public PlannerInputDto withBudget(int nextBudget) {
        return new PlannerInputDto(prompt, nextBudget, roomType, style, location, size, retailerMode, selectedRetailers, optimizationGoal, furnishingLevel, mustHaveCategories, alreadyHaveCategories, lockedProductIds).normalized();
    }

    public PlannerInputDto withSize(int nextSize) {
        return new PlannerInputDto(prompt, budget, roomType, style, location, nextSize, retailerMode, selectedRetailers, optimizationGoal, furnishingLevel, mustHaveCategories, alreadyHaveCategories, lockedProductIds).normalized();
    }

    public PlannerInputDto withRoomType(String nextRoomType) {
        return new PlannerInputDto(prompt, budget, nextRoomType, style, location, size, retailerMode, selectedRetailers, optimizationGoal, furnishingLevel, mustHaveCategories, alreadyHaveCategories, lockedProductIds).normalized();
    }

    public PlannerInputDto withStyle(String nextStyle) {
        return new PlannerInputDto(prompt, budget, roomType, nextStyle, location, size, retailerMode, selectedRetailers, optimizationGoal, furnishingLevel, mustHaveCategories, alreadyHaveCategories, lockedProductIds).normalized();
    }

    public PlannerInputDto withRetailers(String nextRetailerMode, List<String> nextRetailers) {
        return new PlannerInputDto(prompt, budget, roomType, style, location, size, nextRetailerMode, nextRetailers, optimizationGoal, furnishingLevel, mustHaveCategories, alreadyHaveCategories, lockedProductIds).normalized();
    }

    public PlannerInputDto withOptimizationGoal(String nextGoal) {
        return new PlannerInputDto(prompt, budget, roomType, style, location, size, retailerMode, selectedRetailers, nextGoal, furnishingLevel, mustHaveCategories, alreadyHaveCategories, lockedProductIds).normalized();
    }

    public PlannerInputDto withFurnishingLevel(String nextFurnishingLevel) {
        return new PlannerInputDto(prompt, budget, roomType, style, location, size, retailerMode, selectedRetailers, optimizationGoal, nextFurnishingLevel, mustHaveCategories, alreadyHaveCategories, lockedProductIds).normalized();
    }

    public PlannerInputDto withCategories(List<String> nextMustHave, List<String> nextAlreadyHave) {
        return new PlannerInputDto(prompt, budget, roomType, style, location, size, retailerMode, selectedRetailers, optimizationGoal, furnishingLevel, nextMustHave, nextAlreadyHave, lockedProductIds).normalized();
    }

    public PlannerInputDto withLockedProductIds(List<String> nextLockedProductIds) {
        return new PlannerInputDto(prompt, budget, roomType, style, location, size, retailerMode, selectedRetailers, optimizationGoal, furnishingLevel, mustHaveCategories, alreadyHaveCategories, nextLockedProductIds).normalized();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
