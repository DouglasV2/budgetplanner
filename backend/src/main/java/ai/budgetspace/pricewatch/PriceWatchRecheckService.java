package ai.budgetspace.pricewatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 10.34 — the scheduled price-drop re-check. For every active watch it reads the live price with
 * the deterministic {@link LivePriceProbe}, compares it to the stored baseline and, on a real drop past
 * the user's threshold, sends an alert via the {@link PriceWatchNotifier} — honouring a frequency cap
 * (owner decision: notify at most once per product per cooldown window, and never re-notify the same or a
 * higher price).
 *
 * <p>The scheduled trigger is OFF by default ({@code budgetspace.price-watch.recheck-enabled=false}) so
 * the app makes no surprise outbound requests before launch; the logic is fully exercisable via
 * {@link #runRecheck(Instant)} (that is what the tests call). When a real email provider and the flag are
 * enabled, the same path delivers live alerts.</p>
 */
@Service
public class PriceWatchRecheckService {
    private static final Logger log = LoggerFactory.getLogger(PriceWatchRecheckService.class);

    private final PriceWatchRepository repository;
    private final LivePriceProbe probe;
    private final PriceWatchNotifier notifier;
    private final boolean enabled;
    private final int cooldownDays;

    public PriceWatchRecheckService(
            PriceWatchRepository repository,
            LivePriceProbe probe,
            PriceWatchNotifier notifier,
            @Value("${budgetspace.price-watch.recheck-enabled:false}") boolean enabled,
            @Value("${budgetspace.price-watch.notify-cooldown-days:7}") int cooldownDays) {
        this.repository = repository;
        this.probe = probe;
        this.notifier = notifier;
        this.enabled = enabled;
        this.cooldownDays = cooldownDays;
    }

    /** Daily trigger (07:00 by default). No-ops unless explicitly enabled, so nothing fetches by surprise. */
    @Scheduled(cron = "${budgetspace.price-watch.recheck-cron:0 0 7 * * *}")
    public void scheduledRecheck() {
        if (!enabled) {
            log.debug("Price-watch re-check skipped: budgetspace.price-watch.recheck-enabled=false.");
            return;
        }
        RecheckSummary summary = runRecheck(Instant.now());
        log.info("Price-watch re-check done: {}", summary);
    }

    /**
     * Evaluate every active watch against the live price. Returns a summary; sends an alert (through the
     * notifier seam) for each watch whose price dropped past its threshold and is outside the cooldown.
     */
    public RecheckSummary runRecheck(Instant now) {
        List<PriceWatch> watches = repository.findByActiveTrue();
        int checked = 0, drops = 0, notified = 0, skippedCooldown = 0, priceUnavailable = 0;

        for (PriceWatch watch : watches) {
            checked++;
            BigDecimal baseline = watch.getBaselinePrice();
            if (baseline == null || baseline.signum() <= 0) { priceUnavailable++; continue; }

            Optional<BigDecimal> live = probe.currentPrice(watch.getProductUrl(), watch.getRetailer());
            if (live.isEmpty()) { priceUnavailable++; continue; }
            BigDecimal current = live.get();

            // A real drop past the user's threshold? Compare precisely (no rounding boundary surprises).
            BigDecimal maxAllowed = baseline
                    .multiply(BigDecimal.valueOf(100L - watch.getThresholdPercent()))
                    .divide(BigDecimal.valueOf(100L), 4, RoundingMode.HALF_UP);
            if (current.compareTo(maxAllowed) > 0) continue; // drop smaller than threshold (or no drop)
            drops++;

            // Frequency cap: at most once per cooldown window, and never re-alert the same/higher price.
            if (withinCooldown(watch, now)) { skippedCooldown++; continue; }
            if (watch.getLastNotifiedPrice() != null && current.compareTo(watch.getLastNotifiedPrice()) >= 0) {
                skippedCooldown++;
                continue;
            }

            int dropPercent = percentDrop(baseline, current);
            notifier.notifyPriceDrop(new PriceWatchNotification(
                    watch.getEmail(), watch.getProductName(), watch.getProductUrl(), watch.getMarket(),
                    baseline, current, dropPercent, watch.getUnsubscribeToken()));
            watch.setLastNotifiedAt(now.toString());
            watch.setLastNotifiedPrice(current);
            repository.save(watch);
            notified++;
        }
        return new RecheckSummary(checked, drops, notified, skippedCooldown, priceUnavailable);
    }

    private boolean withinCooldown(PriceWatch watch, Instant now) {
        String last = watch.getLastNotifiedAt();
        if (last == null || last.isBlank()) return false;
        try {
            return Instant.parse(last).plus(Duration.ofDays(cooldownDays)).isAfter(now);
        } catch (Exception e) {
            return false; // an unparseable stamp must not block a real alert
        }
    }

    static int percentDrop(BigDecimal baseline, BigDecimal current) {
        return baseline.subtract(current)
                .multiply(BigDecimal.valueOf(100))
                .divide(baseline, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** A run summary (also handy for the manual/admin trigger and tests). */
    public record RecheckSummary(int checked, int drops, int notified, int skippedCooldown, int priceUnavailable) {
    }
}
