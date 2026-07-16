package ai.budgetspace.product;

import ai.budgetspace.dto.CatalogHealthDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sprint 10.166 — weekly catalog HEALTH AUDIT + alert. Complements the rolling {@link CatalogFreshnessService}
 * (which re-probes live prices) with a cheap, network-free health check: it reads the current catalog state via
 * {@link CatalogHealthService} (no outbound fetch) and, on a schedule, logs a summary and escalates to a WARN
 * when too much of the catalog has gone stale. This is the
 * "N products are outdated, refresh the catalog" signal an operator needs, without any request-path cost or
 * retailer hammering.
 *
 * <p>OFF by default ({@code budgetspace.catalog.audit-enabled=false}) like the other schedulers; the prod profile
 * turns it on. Read-only — it never mutates the catalog and holds only the (small) product list transiently, so
 * it is safe on a memory-constrained host. The core {@link #runAudit()} is fully unit-testable with a stubbed
 * {@link CatalogHealthService} (no DB, no network). Runs because the app has {@code @EnableScheduling}.</p>
 */
@Service
public class CatalogAuditService {
    private static final Logger log = LoggerFactory.getLogger(CatalogAuditService.class);

    private final CatalogHealthService catalogHealthService;
    private final boolean enabled;
    private final double staleThreshold;

    public CatalogAuditService(
            CatalogHealthService catalogHealthService,
            @Value("${budgetspace.catalog.audit-enabled:false}") boolean enabled,
            @Value("${budgetspace.catalog.audit-stale-threshold:0.2}") double staleThreshold) {
        this.catalogHealthService = catalogHealthService;
        this.enabled = enabled;
        // Clamp to [0,1] so a mis-set env can't make the alert impossible (>1) or always-on-at-zero in a surprising way.
        this.staleThreshold = Math.min(1.0, Math.max(0.0, staleThreshold));
    }

    /** Weekly trigger (Monday 04:00 by default). No-ops unless explicitly enabled, so nothing runs by surprise. */
    @Scheduled(cron = "${budgetspace.catalog.audit-cron:0 0 4 * * 1}")
    public void scheduledAudit() {
        if (!enabled) {
            log.debug("Catalog audit skipped: budgetspace.catalog.audit-enabled=false.");
            return;
        }
        runAudit();
    }

    /**
     * Compute a catalog-health snapshot and log it — INFO normally, WARN when the stale share
     * crosses the threshold. Returns the report so a manual/admin trigger and tests can read it directly.
     */
    public AuditReport runAudit() {
        CatalogHealthDto health = catalogHealthService.compute();
        int total = health.totalProducts();
        int stale = health.staleProducts();
        double staleFraction = total == 0 ? 0.0 : (double) stale / total;
        boolean alert = total > 0 && staleFraction >= staleThreshold;
        AuditReport report = new AuditReport(total, stale, health.unavailableProducts(),
                health.needsReviewProducts(), health.missingUrlProducts(), staleFraction, alert);

        if (alert) {
            log.warn("Catalog audit ALERT — {}/{} products stale ({}%, threshold {}%), {} unavailable, {} need review. "
                            + "Refresh the catalog (rolling freshness re-check and/or a collector run).",
                    stale, total, pct(staleFraction), pct(staleThreshold),
                    health.unavailableProducts(), health.needsReviewProducts());
        } else {
            log.info("Catalog audit — {}/{} products stale ({}%), {} unavailable, {} need review.",
                    stale, total, pct(staleFraction), health.unavailableProducts(), health.needsReviewProducts());
        }
        return report;
    }

    private static long pct(double fraction) {
        return Math.round(fraction * 100);
    }

    /** Audit outcome — {@code alert} is true when {@code staleFraction >= threshold}. Serialisable for the admin endpoint. */
    public record AuditReport(int totalProducts, int staleProducts, int unavailableProducts,
                              int needsReviewProducts, int missingUrlProducts, double staleFraction, boolean alert) {
    }
}
