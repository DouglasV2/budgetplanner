package ai.budgetspace.dto;

/**
 * Request for the dev-only category discovery endpoint. It asks the backend to fetch a
 * single category or listing page from a supported retailer, extract a small number of
 * product URLs, and optionally pass them directly into the collector.
 *
 * <p>The discovery runs are deliberately conservative: only one page is read, no
 * pagination is followed, and the result list is capped. Defaults for category, room
 * tags and style tags can be provided to prefill the collector when {@code collect}
 * is {@code true}. If {@code collect} is {@code false} or omitted, the discovery
 * endpoint simply returns the found URLs without importing products.</p>
 */
public record DiscoveryRequestDto(
        String retailer,
        String categoryUrl,
        Integer limit,
        Boolean collect,
        CollectorDefaultsDto defaults
) {
}