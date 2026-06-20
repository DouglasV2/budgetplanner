package ai.budgetspace.saved;

/**
 * Sprint 10.68 — a Free-tier owner tried to save more than the allowed number of plans. Mapped to HTTP 402
 * (Payment Required) by the global handler so the frontend can show the Plus upsell instead of a generic error.
 */
public class PlanLimitReachedException extends RuntimeException {
    private final int limit;

    public PlanLimitReachedException(int limit) {
        super("Free plan save limit reached (" + limit + ")");
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
