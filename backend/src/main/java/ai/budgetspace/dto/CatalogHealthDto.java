package ai.budgetspace.dto;

import java.util.List;
import java.util.Map;

/**
 * Dev-only catalog health report (Sprint 9.6). Tells us whether the catalog is good enough
 * for the planner: how many products are usable vs blocked, how the usable ones spread across
 * rooms / categories / retailers, and per-room readiness. Not a user-facing screen.
 */
public record CatalogHealthDto(
        int totalProducts,
        int usableProducts,
        int blockedProducts,
        int unavailableProducts,
        int needsReviewProducts,
        int staleProducts,
        int missingUrlProducts,
        int missingImageProducts,
        Map<String, Integer> byRoom,
        Map<String, Integer> byCategory,
        Map<String, Integer> byRetailer,
        Map<String, Integer> byDataQuality,
        List<RoomReadinessDto> plannerReadiness
) {
}
