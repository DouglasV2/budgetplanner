package ai.budgetspace.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sprint 10.98 — runs the Stripe subscription reconciliation ({@link BillingService#reconcileSubscriptions()}) on a
 * schedule, as a backstop for any dropped/missed webhook. It is a no-op unless Stripe is configured, so nothing
 * calls Stripe by surprise in dev. The cron is overridable via {@code budgetspace.stripe.reconcile-cron} (default:
 * every 6 hours). Runs because the app has {@code @EnableScheduling}.
 */
@Service
public class BillingReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(BillingReconciliationService.class);

    private final BillingService billingService;

    public BillingReconciliationService(BillingService billingService) {
        this.billingService = billingService;
    }

    @Scheduled(cron = "${budgetspace.stripe.reconcile-cron:0 0 */6 * * *}")
    public void reconcile() {
        try {
            billingService.reconcileSubscriptions();
        } catch (Exception exception) {
            // The sweep already skips per-account errors; this guards against an unexpected top-level failure so the
            // scheduler keeps firing on the next interval.
            log.warn("Stripe reconciliation run failed — will retry next schedule.", exception);
        }
    }
}
