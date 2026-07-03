package ai.budgetspace.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Sprint 10.163 — GDPR Art. 5(1)(e) "storage limitation" for the pseudonymous analytics + the Plus waitlist.
 * Sessions and the AI ledger are already pruned elsewhere; this daily sweep bounds the three tables that had no
 * retention limit: {@code product_clicks}, {@code plan_feedback} and {@code plus_interest}. The waitlist in
 * particular holds an OPTIONAL email (real PII) that must not outlive any relationship — so old rows are dropped
 * past a configurable window (default 18 months).
 *
 * <p>Mirrors {@link ai.budgetspace.auth.AuthSessionCleanupService}: a plain scheduled sweep that runs only because
 * the app has {@code @EnableScheduling}, with the cron overridable via env. It is additionally gated by an enable
 * flag (default ON) so an operator can disable it without a code change. The core logic is exercisable via
 * {@link #runCleanup(Instant)} (that is what the test calls).</p>
 */
@Service
public class RetentionCleanupService {
    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupService.class);

    private final ProductClickRepository productClickRepository;
    private final PlanFeedbackRepository planFeedbackRepository;
    private final PlusInterestRepository plusInterestRepository;
    private final boolean enabled;
    private final int retentionMonths;

    public RetentionCleanupService(
            ProductClickRepository productClickRepository,
            PlanFeedbackRepository planFeedbackRepository,
            PlusInterestRepository plusInterestRepository,
            @Value("${budgetspace.retention.cleanup-enabled:true}") boolean enabled,
            @Value("${budgetspace.retention.months:18}") int retentionMonths) {
        this.productClickRepository = productClickRepository;
        this.planFeedbackRepository = planFeedbackRepository;
        this.plusInterestRepository = plusInterestRepository;
        this.enabled = enabled;
        this.retentionMonths = retentionMonths;
    }

    /** Daily trigger (03:45 by default, staggered after the session sweep). No-ops unless enabled. */
    @Scheduled(cron = "${budgetspace.retention.cleanup-cron:0 45 3 * * *}")
    public void scheduledCleanup() {
        if (!enabled) {
            log.debug("Retention cleanup skipped: budgetspace.retention.cleanup-enabled=false.");
            return;
        }
        runCleanup(Instant.now());
    }

    /**
     * Delete analytics + waitlist rows older than the retention window relative to {@code now}. Each table is
     * pruned independently; a failure on one is logged and does not block the others (best-effort housekeeping).
     */
    public void runCleanup(Instant now) {
        // Instant can't subtract calendar months directly (month length varies), so step back via a zoned date.
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(retentionMonths).toInstant();
        int clicks = safeDelete("product_clicks", () -> productClickRepository.deleteByCreatedAtBefore(cutoff));
        int feedback = safeDelete("plan_feedback", () -> planFeedbackRepository.deleteByCreatedAtBefore(cutoff));
        int interest = safeDelete("plus_interest", () -> plusInterestRepository.deleteByCreatedAtBefore(cutoff));
        if (clicks + feedback + interest > 0) {
            log.info("Retention cleanup (>{}mo): productClicks={}, planFeedback={}, plusInterest={}.",
                    retentionMonths, clicks, feedback, interest);
        }
    }

    private int safeDelete(String table, java.util.function.IntSupplier delete) {
        try {
            return delete.getAsInt();
        } catch (RuntimeException exception) {
            log.warn("Retention cleanup failed for {} — skipping this table this run.", table, exception);
            return 0;
        }
    }
}
