package ai.budgetspace.dto;

import java.util.List;

/**
 * Result of one collector run (Sprint 9.2, v2). Says clearly what was fetched, parsed,
 * imported, updated, skipped and left for review, with per-product reports and warnings.
 * {@link #importSummary()} is the same summary the regular import returns, so created /
 * updated / skipped and per-product errors stay consistent.
 */
public record CollectorRunSummaryDto(
        String runId,
        String startedAt,
        String finishedAt,
        String retailer,
        int totalReceived,
        int fetched,
        int parsed,
        int imported,
        int updated,
        int skipped,
        int needsReview,
        List<CollectorErrorDto> errors,
        List<String> warnings,
        ImportSummaryDto importSummary,
        List<CollectorProductReportDto> products,
        List<CollectorReviewItemDto> reviewItems
) {
}
