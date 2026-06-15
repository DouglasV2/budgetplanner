package ai.budgetspace.ai;

import java.time.Instant;

/**
 * Sprint 10.10 — one recorded AI (or fallback) call, for cost/usage tracking and monetization
 * groundwork. Kept in memory (see {@link AiUsageTracker}); no DB migration yet. No prompt text or
 * API key is stored — only metadata.
 */
public record AiUsageEvent(
        String provider,
        String model,
        String useCase,
        String sessionId,
        Integer inputTokens,
        Integer outputTokens,
        double estimatedCostUsd,
        boolean success,
        boolean fallbackUsed,
        Instant createdAt
) {
}
