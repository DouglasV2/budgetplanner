package ai.budgetspace.dto;

import java.time.Instant;

/** Read view of a persisted collector run header (Sprint 9.5). */
public record CollectorRunDto(
        String runId,
        String retailer,
        String startedAt,
        String finishedAt,
        int totalReceived,
        int fetched,
        int parsed,
        int imported,
        int updated,
        int skipped,
        int needsReview,
        String requestSummary,
        Instant createdAt
) {
}
