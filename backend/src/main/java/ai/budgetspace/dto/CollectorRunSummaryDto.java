package ai.budgetspace.dto;

import java.util.List;

/**
 * Result of one collector run. {@link #importSummary()} is the same summary shape the
 * regular import returns, so created/updated/skipped and per-product errors stay consistent.
 */
public record CollectorRunSummaryDto(
        int totalReceived,
        int collected,
        int imported,
        int skipped,
        List<CollectorErrorDto> errors,
        ImportSummaryDto importSummary
) {
}
