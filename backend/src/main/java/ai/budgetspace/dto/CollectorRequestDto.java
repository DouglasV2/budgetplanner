package ai.budgetspace.dto;

import java.util.List;

/**
 * Input for the controlled retailer collector (Sprint 9.1).
 *
 * <p>The collector works over an <strong>explicit, small list of product URLs</strong> —
 * never a category or search. It is a dev-only tool: there is no UI and no crawling.</p>
 */
public record CollectorRequestDto(
        String retailer,
        List<String> urls,
        CollectorDefaultsDto defaults
) {
}
