package ai.budgetspace.ai;

import java.time.Instant;

/**
 * Sprint 10.10 — one recorded AI (or fallback) call, for cost/usage tracking and monetization
 * groundwork. The live counters live in memory (see {@link AiUsageTracker}); since Sprint 10.86 each real
 * event is also persisted as an {@link AiUsageRecord} so caps survive restarts. No prompt text or API key is
 * stored — only metadata.
 *
 * <p>Sprint 10.70: {@code ownerKey} (the account "user:&lt;id&gt;" or guest "guest:&lt;browserId&gt;") and
 * {@code tier} (GUEST / FREE / PLUS / PRO) replace the old raw session id, so daily caps can be enforced
 * per user and per subscription tier.</p>
 */
public record AiUsageEvent(
        String provider,
        String model,
        String useCase,
        String ownerKey,
        String tier,
        Integer inputTokens,
        Integer outputTokens,
        double estimatedCostUsd,
        boolean success,
        boolean fallbackUsed,
        Instant createdAt
) {
}
