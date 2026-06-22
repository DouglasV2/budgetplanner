package ai.budgetspace.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.86 — the durable AI usage ledger: real calls write through to the repository (fallbacks don't), and
 * the per-user/monthly counters are rehydrated from it on startup so caps survive a restart.
 */
class AiUsageTrackerTest {

    private static AiUsageEvent realEvent(String owner, String tier, Instant at) {
        return new AiUsageEvent("GEMINI", "gemini-flash", "prompt-intel", owner, tier, 100, 50, 0.0001, true, false, at);
    }

    private static AiUsageEvent fallbackEvent(String owner, String tier) {
        return new AiUsageEvent(null, null, "prompt-intel", owner, tier, null, null, 0.0, false, true, Instant.now());
    }

    @Test
    void writesRealEventsThroughToTheLedgerButNotFallbacks() {
        AiUsageRecordRepository repository = mock(AiUsageRecordRepository.class);
        AiUsageTracker tracker = new AiUsageTracker(20, 2000, 3, 10, 100, 500, 0.0002, 0.0008, repository);

        tracker.complete("user:x", realEvent("user:x", "FREE", Instant.now()));
        verify(repository).save(any(AiUsageRecord.class));

        // A fallback call is not a billable/counted event — it must not hit the ledger.
        tracker.complete("user:x", fallbackEvent("user:x", "FREE"));
        verify(repository, times(1)).save(any(AiUsageRecord.class));
    }

    @Test
    void rehydrateRestoresPerOwnerDailyCountsFromTheLedger() {
        AiUsageRecordRepository repository = mock(AiUsageRecordRepository.class);
        // Two real calls already made today by this owner, persisted before a restart.
        when(repository.findTop5000ByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any()))
                .thenReturn(List.of(
                        AiUsageRecord.from(realEvent("user:x", "FREE", Instant.now())),
                        AiUsageRecord.from(realEvent("user:x", "FREE", Instant.now()))));
        // FREE daily limit = 2.
        AiUsageTracker tracker = new AiUsageTracker(20, 2000, 3, 2, 100, 500, 0.0002, 0.0008, repository);

        tracker.rehydrate();

        // The two rehydrated events already fill the FREE cap → the next acquire is denied (cap survived restart).
        assertThat(tracker.tryAcquire("user:x", "FREE")).isFalse();
        // A different owner is unaffected.
        assertThat(tracker.tryAcquire("user:y", "FREE")).isTrue();
    }

    @Test
    void pureInMemoryConstructorNeedsNoRepository() {
        AiUsageTracker tracker = new AiUsageTracker(20, 2000, 3, 10, 100, 500, 0.0002, 0.0008);

        tracker.rehydrate(); // no-op without a repository, must not NPE
        assertThat(tracker.tryAcquire("user:x", "FREE")).isTrue();
        tracker.complete("user:x", realEvent("user:x", "FREE", Instant.now())); // must not NPE without a repository
    }
}
