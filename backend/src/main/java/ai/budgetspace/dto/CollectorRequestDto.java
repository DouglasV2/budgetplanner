package ai.budgetspace.dto;

import java.util.List;

/**
 * Input for the controlled retailer collector.
 *
 * <p>The collector works over an <strong>explicit, small list of product URLs</strong> —
 * never a category or search. It is a dev-only tool: there is no UI and no crawling.</p>
 *
 * <p>Two shapes are supported (Sprint 9.4):</p>
 * <ul>
 *   <li>{@code urls} + request-level {@code defaults} (the original shape), and</li>
 *   <li>{@code items}, where each URL can carry its own {@code defaults} so one request can
 *       mix categories. If {@code items} is present it wins; otherwise {@code urls} is used.</li>
 * </ul>
 */
public record CollectorRequestDto(
        String retailer,
        List<String> urls,
        CollectorDefaultsDto defaults,
        List<CollectorItemDto> items
) {
}
