package ai.budgetspace.dto;

import java.util.List;

/**
 * Dev-facing report for one URL in a collector run. {@code status} is one of
 * {@code fetched}, {@code parsed}, {@code imported}, {@code updated}, {@code skipped} or
 * {@code needs-review}. Not shown to the end user.
 */
public record CollectorProductReportDto(
        String url,
        String externalId,
        String name,
        String status,
        String message,
        String dataQuality,
        List<String> warnings
) {
}
