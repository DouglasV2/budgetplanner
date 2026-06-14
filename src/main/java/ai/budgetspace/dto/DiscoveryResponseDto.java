package ai.budgetspace.dto;

import java.util.List;

import ai.budgetspace.dto.CollectorRunSummaryDto;

/**
 * Response from the dev-only category discovery endpoint. It reports which product URLs
 * were found on the given category page, what was skipped, and any warnings or errors
 * encountered. When {@code collect} was {@code true} in the request and products were
 * imported, {@code collectorSummary} carries the collector run summary; otherwise it is
 * {@code null}.
 */
public record DiscoveryResponseDto(
        String runId,
        String retailer,
        String categoryUrl,
        List<String> foundUrls,
        Integer limit,
        List<String> skippedUrls,
        List<String> warnings,
        List<String> errors,
        CollectorRunSummaryDto collectorSummary
) {
}