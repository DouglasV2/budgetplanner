package ai.budgetspace.dto;

import ai.budgetspace.pricewatch.PriceWatch;

import java.math.BigDecimal;

/**
 * Sprint 10.34 — what we return after creating (or finding) a price watch. Deliberately omits the
 * unsubscribe token (that secret only travels in the alert email) and echoes just enough for the UI to
 * confirm the watch. {@code alreadyWatching} is true when an identical active watch already existed.
 */
public record PriceWatchDto(
        String id,
        String productName,
        String email,
        int thresholdPercent,
        BigDecimal baselinePrice,
        boolean active,
        boolean alreadyWatching,
        String createdAt
) {
    public static PriceWatchDto of(PriceWatch watch, boolean alreadyWatching) {
        return new PriceWatchDto(
                watch.getId(),
                watch.getProductName(),
                watch.getEmail(),
                watch.getThresholdPercent(),
                watch.getBaselinePrice(),
                watch.isActive(),
                alreadyWatching,
                watch.getCreatedAt()
        );
    }
}
