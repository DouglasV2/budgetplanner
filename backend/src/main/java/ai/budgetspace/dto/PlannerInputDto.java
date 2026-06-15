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
        List<String> lockedProductIds,
        List<String> preferredRetailers,
        List<String> excludedRetailers,
        int maxStores,
        // Sprint 10.7: colour/material preferences parsed from the prompt (canonical keys, e.g.
        // "green", "wood"). Used by PlannerService.scoreProduct to gently prefer matching products.
        List<String> colorPreferences,
        List<String> materialPreferences
) {
    /**
     * Backwards-compatible constructor for callers created before Sprint 10.7 added colour/material
     * preferences (the frontend, tests and scenario fixtures). Both preference lists default to empty.
     */
    public PlannerInputDto(
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
            List<String> lockedProductIds,
            List<String> preferredRetailers,
            List<String> excludedRetailers,
            int maxStores
    ) {
        this(prompt, budget, roomType, style, location, size, retailerMode, selectedRetailers,
                optimizationGoal, furnishingLevel, mustHaveCategories, alreadyHaveCategories,
                lockedProductIds, preferredRetailers, excludedRetailers, maxStores, List.of(), List.of());
    }

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
                lockedProductIds == null ? List.of() : lockedProductIds,
                preferredRetailers == null ? List.of() : preferredRetailers,
                excludedRetailers == null ? List.of() : excludedRetailers,
                Math.max(0, maxStores),
                colorPreferences == null ? List.of() : colorPreferences,
                materialPreferences == null ? List.of() : materialPreferences
        );
    }

    public PlannerInputDto withBudget(int nextBudget) {
        return copy().budget(nextBudget).build();
    }

    public PlannerInputDto withSize(int nextSize) {
        return copy().size(nextSize).build();
    }

    public PlannerInputDto withRoomType(String nextRoomType) {
        return copy().roomType(nextRoomType).build();
    }

    public PlannerInputDto withStyle(String nextStyle) {
        return copy().style(nextStyle).build();
    }

    public PlannerInputDto withRetailers(String nextRetailerMode, List<String> nextRetailers) {
        return copy().retailerMode(nextRetailerMode).selectedRetailers(nextRetailers).build();
    }

    public PlannerInputDto withRetailerIntent(String nextRetailerMode, List<String> nextRetailers, List<String> nextPreferred, List<String> nextExcluded, int nextMaxStores) {
        return copy()
                .retailerMode(nextRetailerMode)
                .selectedRetailers(nextRetailers)
                .preferredRetailers(nextPreferred)
                .excludedRetailers(nextExcluded)
                .maxStores(nextMaxStores)
                .build();
    }

    public PlannerInputDto withOptimizationGoal(String nextGoal) {
        return copy().optimizationGoal(nextGoal).build();
    }

    public PlannerInputDto withFurnishingLevel(String nextFurnishingLevel) {
        return copy().furnishingLevel(nextFurnishingLevel).build();
    }

    public PlannerInputDto withCategories(List<String> nextMustHave, List<String> nextAlreadyHave) {
        return copy().mustHaveCategories(nextMustHave).alreadyHaveCategories(nextAlreadyHave).build();
    }

    public PlannerInputDto withLockedProductIds(List<String> nextLockedProductIds) {
        return copy().lockedProductIds(nextLockedProductIds).build();
    }

    public PlannerInputDto withColorAndMaterialPreferences(List<String> nextColors, List<String> nextMaterials) {
        return copy().colorPreferences(nextColors).materialPreferences(nextMaterials).build();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Builder copy() {
        return new Builder(this);
    }

    /**
     * Small mutable builder so the {@code with*} helpers stay readable now that the
     * record has more fields. Always runs {@link #normalized()} on build.
     */
    private static final class Builder {
        private String prompt;
        private int budget;
        private String roomType;
        private String style;
        private String location;
        private int size;
        private String retailerMode;
        private List<String> selectedRetailers;
        private String optimizationGoal;
        private String furnishingLevel;
        private List<String> mustHaveCategories;
        private List<String> alreadyHaveCategories;
        private List<String> lockedProductIds;
        private List<String> preferredRetailers;
        private List<String> excludedRetailers;
        private int maxStores;
        private List<String> colorPreferences;
        private List<String> materialPreferences;

        private Builder(PlannerInputDto source) {
            this.prompt = source.prompt();
            this.budget = source.budget();
            this.roomType = source.roomType();
            this.style = source.style();
            this.location = source.location();
            this.size = source.size();
            this.retailerMode = source.retailerMode();
            this.selectedRetailers = source.selectedRetailers();
            this.optimizationGoal = source.optimizationGoal();
            this.furnishingLevel = source.furnishingLevel();
            this.mustHaveCategories = source.mustHaveCategories();
            this.alreadyHaveCategories = source.alreadyHaveCategories();
            this.lockedProductIds = source.lockedProductIds();
            this.preferredRetailers = source.preferredRetailers();
            this.excludedRetailers = source.excludedRetailers();
            this.maxStores = source.maxStores();
            this.colorPreferences = source.colorPreferences();
            this.materialPreferences = source.materialPreferences();
        }

        private Builder budget(int value) { this.budget = value; return this; }
        private Builder size(int value) { this.size = value; return this; }
        private Builder roomType(String value) { this.roomType = value; return this; }
        private Builder style(String value) { this.style = value; return this; }
        private Builder retailerMode(String value) { this.retailerMode = value; return this; }
        private Builder selectedRetailers(List<String> value) { this.selectedRetailers = value; return this; }
        private Builder optimizationGoal(String value) { this.optimizationGoal = value; return this; }
        private Builder furnishingLevel(String value) { this.furnishingLevel = value; return this; }
        private Builder mustHaveCategories(List<String> value) { this.mustHaveCategories = value; return this; }
        private Builder alreadyHaveCategories(List<String> value) { this.alreadyHaveCategories = value; return this; }
        private Builder lockedProductIds(List<String> value) { this.lockedProductIds = value; return this; }
        private Builder preferredRetailers(List<String> value) { this.preferredRetailers = value; return this; }
        private Builder excludedRetailers(List<String> value) { this.excludedRetailers = value; return this; }
        private Builder maxStores(int value) { this.maxStores = value; return this; }
        private Builder colorPreferences(List<String> value) { this.colorPreferences = value; return this; }
        private Builder materialPreferences(List<String> value) { this.materialPreferences = value; return this; }

        private PlannerInputDto build() {
            return new PlannerInputDto(
                    prompt, budget, roomType, style, location, size, retailerMode, selectedRetailers,
                    optimizationGoal, furnishingLevel, mustHaveCategories, alreadyHaveCategories, lockedProductIds,
                    preferredRetailers, excludedRetailers, maxStores, colorPreferences, materialPreferences
            ).normalized();
        }
    }
}
