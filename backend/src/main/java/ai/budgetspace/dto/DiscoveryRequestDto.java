package ai.budgetspace.dto;

/**
 * Input for controlled product-URL discovery (dev-only). Reads <strong>one</strong> listing
 * page and returns the product URLs found on it. It does not paginate, follow links or crawl.
 */
public record DiscoveryRequestDto(
        String retailer,
        String listingUrl,
        Integer maxUrls
) {
}
