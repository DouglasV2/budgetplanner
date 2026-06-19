package ai.budgetspace.dto;

public record SavedPlanRequest(FurnishingPlanDto plan, PlannerInputDto input, String spaceName) {
    /** Backward-compatible (pre-10.61): no space — the plan groups under the default space client-side. */
    public SavedPlanRequest(FurnishingPlanDto plan, PlannerInputDto input) {
        this(plan, input, null);
    }
}
