package ai.budgetspace.tracking;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.163 — the retention sweep prunes the three previously-unbounded analytics/waitlist tables past the
 * configured window (default 18 months), keeping storage GDPR-compliant (Art. 5(1)(e)). The enable flag gates it.
 */
class RetentionCleanupServiceTest {

    private final ProductClickRepository productClickRepository = mock(ProductClickRepository.class);
    private final PlanFeedbackRepository planFeedbackRepository = mock(PlanFeedbackRepository.class);
    private final PlusInterestRepository plusInterestRepository = mock(PlusInterestRepository.class);

    @Test
    void runCleanupDeletesEachTableWithAnEighteenMonthCutoff() {
        RetentionCleanupService service = new RetentionCleanupService(
                productClickRepository, planFeedbackRepository, plusInterestRepository, true, 18);
        Instant now = Instant.parse("2026-07-03T00:00:00Z");

        service.runCleanup(now);

        // Each table is pruned with the same cutoff = now minus 18 months (2025-01-03).
        Instant expectedCutoff = now.atZone(ZoneOffset.UTC).minusMonths(18).toInstant();
        for (var repo : new Object[]{productClickRepository, planFeedbackRepository, plusInterestRepository}) {
            ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
            if (repo == productClickRepository) {
                verify(productClickRepository).deleteByCreatedAtBefore(cutoff.capture());
            } else if (repo == planFeedbackRepository) {
                verify(planFeedbackRepository).deleteByCreatedAtBefore(cutoff.capture());
            } else {
                verify(plusInterestRepository).deleteByCreatedAtBefore(cutoff.capture());
            }
            assertThat(cutoff.getValue()).isEqualTo(expectedCutoff);
        }
    }

    @Test
    void oneRepositoryFailingDoesNotBlockTheOthers() {
        // Best-effort housekeeping: a failure on one table must not stop the other two from being pruned.
        when(productClickRepository.deleteByCreatedAtBefore(any())).thenThrow(new RuntimeException("db down"));
        RetentionCleanupService service = new RetentionCleanupService(
                productClickRepository, planFeedbackRepository, plusInterestRepository, true, 18);

        service.runCleanup(Instant.parse("2026-07-03T00:00:00Z"));

        verify(planFeedbackRepository).deleteByCreatedAtBefore(any());
        verify(plusInterestRepository).deleteByCreatedAtBefore(any());
    }

    @Test
    void scheduledCleanupIsANoOpWhenDisabled() {
        RetentionCleanupService service = new RetentionCleanupService(
                productClickRepository, planFeedbackRepository, plusInterestRepository, false, 18);

        service.scheduledCleanup();

        verify(productClickRepository, never()).deleteByCreatedAtBefore(any());
        verify(planFeedbackRepository, never()).deleteByCreatedAtBefore(any());
        verify(plusInterestRepository, never()).deleteByCreatedAtBefore(any());
    }
}
