package ai.budgetspace.product;

import ai.budgetspace.pricewatch.LivePriceProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 10.156 — rolling catalog FRESHNESS re-check. Keeps stored prices honest over time WITHOUT touching
 * the request path (the planner reads the cached DB; a user request never waits on an outbound fetch).
 *
 * <p>Each scheduled run re-verifies the {@code batchSize} products with the OLDEST {@code lastCheckedAt}
 * (the stalest first) using the deterministic {@link LivePriceProbe} — the same JSON-LD extractor that
 * sources the catalog, so no price is ever fabricated. On a confident read it writes the current price when
 * it changed and re-confirms availability; on an unreadable page (anti-bot 403, dead link, transient error)
 * it does NOT invent a price — it flags the row {@code check-store} so the UI keeps its honest "provjeri u
 * trgovini" note. Either way it stamps {@code lastCheckedAt} so the rolling queue keeps PROGRESSING (an
 * unfetchable row can never wedge the front of the oldest-first queue). Oldest-first naturally drains the
 * stale backlog and holds the whole catalog under a bounded age — tune {@code batchSize} × frequency so
 * {@code batchSize × runsPerDay ≥ catalogSize / targetMaxAgeDays} (default 60 every 6h = 240/day ≈ 3000
 * products under ~12 days, inside the 14-day UI freshness threshold).</p>
 *
 * <p>OFF by default ({@code budgetspace.catalog.freshness-enabled=false}) like the other schedulers, so the
 * app makes no surprise outbound requests; the core {@link #runRefresh(Instant)} is fully testable with a
 * stubbed probe (no network in tests). Runs because the app has {@code @EnableScheduling}.</p>
 */
@Service
public class CatalogFreshnessService {
    private static final Logger log = LoggerFactory.getLogger(CatalogFreshnessService.class);

    private final ProductRepository productRepository;
    private final LivePriceProbe probe;
    private final boolean enabled;
    private final int batchSize;

    public CatalogFreshnessService(
            ProductRepository productRepository,
            LivePriceProbe probe,
            @Value("${budgetspace.catalog.freshness-enabled:false}") boolean enabled,
            @Value("${budgetspace.catalog.freshness-batch-size:60}") int batchSize) {
        this.productRepository = productRepository;
        this.probe = probe;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
    }

    /** Rolling trigger (every 6h by default). No-ops unless explicitly enabled, so nothing fetches by surprise. */
    @Scheduled(cron = "${budgetspace.catalog.freshness-cron:0 20 */6 * * *}")
    public void scheduledRefresh() {
        if (!enabled) {
            log.debug("Catalog freshness re-check skipped: budgetspace.catalog.freshness-enabled=false.");
            return;
        }
        RefreshSummary summary = runRefresh(Instant.now());
        log.info("Catalog freshness re-check done: {}", summary);
    }

    /**
     * Re-verify the stalest {@code batchSize} products against their live price. Only a confidently-read
     * price updates the row; an unreadable one is flagged {@code check-store} (never given a fabricated
     * price). Every checked row's {@code lastCheckedAt} advances so the rolling queue always progresses.
     */
    public RefreshSummary runRefresh(Instant now) {
        List<Product> batch = productRepository.findByOrderByLastCheckedAtAsc(PageRequest.of(0, batchSize));
        String today = LocalDate.ofInstant(now, ZoneOffset.UTC).toString();
        int checked = 0, changed = 0, confirmed = 0, unverifiable = 0;

        for (Product product : batch) {
            checked++;
            Optional<BigDecimal> live = probe.currentPrice(product.getProductUrl(), product.getRetailer());
            if (live.isPresent()) {
                BigDecimal current = live.get();
                if (product.getPrice() == null || current.compareTo(product.getPrice()) != 0) {
                    product.setPrice(current);
                    // The probe reads only the CURRENT price — never assert an unverified promo, so drop any
                    // stored sale. A real sale is re-established only by a window-verified sourcing pass.
                    product.setOriginalPrice(null);
                    product.setSaleEndsAt(null);
                    changed++;
                } else {
                    confirmed++;
                }
                product.setAvailabilityStatus("in-stock"); // a live price means it is purchasable now
                product.setInStock(true);
            } else {
                // Couldn't confidently read (403/anti-bot, dead link, transient) — do NOT fabricate a price.
                // Flag check-store so the UI hedges honestly; still advance lastCheckedAt so the queue moves on.
                product.setAvailabilityStatus("check-store");
                product.setInStock(true);
                unverifiable++;
            }
            product.setLastCheckedAt(today);
            productRepository.save(product);
        }
        return new RefreshSummary(checked, changed, confirmed, unverifiable);
    }

    /** A run summary (also handy for a manual/admin trigger and tests). */
    public record RefreshSummary(int checked, int changed, int confirmed, int unverifiable) {
    }
}
