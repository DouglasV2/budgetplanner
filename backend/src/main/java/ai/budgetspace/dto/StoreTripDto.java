package ai.budgetspace.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Simple "how many shop trips is this plan" summary (Sprint 8.4, store trip v0).
 *
 * <p>No clever route optimisation — just the facts a person needs before they
 * go buy: how many stores, how much in each, which store carries most of the
 * plan, how many items they should check in the store first, and one short
 * human sentence ({@link #recommendation()}) the UI can show above the list.</p>
 */
public record StoreTripDto(
        int storeCount,
        String mainRetailer,
        BigDecimal mainRetailerTotal,
        int checkInStoreCount,
        String recommendation,
        List<StoreTotalDto> stores
) {
}
