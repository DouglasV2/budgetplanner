package ai.budgetspace.dto;

/**
 * Sprint 10.34 — opt-in request to watch a product's price. {@code consent} MUST be {@code true}
 * (explicit GDPR opt-in) or the watch is rejected; {@code thresholdPercent} is optional (defaults to 5).
 */
public record CreatePriceWatchRequest(
        String email,
        String externalId,
        String market,
        Integer thresholdPercent,
        boolean consent
) {
}
