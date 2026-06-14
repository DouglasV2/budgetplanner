package ai.budgetspace.dto;

import java.util.List;

/**
 * Result of controlled product-URL discovery from a single listing page (dev-only).
 * The returned {@code productUrls} can be reviewed by hand and fed into the collector.
 */
public record DiscoveryResponseDto(
        String retailer,
        String listingUrl,
        int found,
        List<String> productUrls,
        String message,
        List<String> warnings
) {
}
