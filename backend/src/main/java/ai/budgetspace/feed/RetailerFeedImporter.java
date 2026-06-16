package ai.budgetspace.feed;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.product.RetailerSnapshotImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 10.14 — runs the configured official/partner feeds on startup, after the real-catalog seed.
 *
 * <p>Design goals, all enforced here:</p>
 * <ul>
 *   <li><strong>Missing feed config never breaks the app.</strong> An unconfigured feed is skipped
 *       cleanly and the reason is logged; the application keeps running.</li>
 *   <li><strong>No fabrication.</strong> Only rows a configured feed actually returns are imported,
 *       through the same validated snapshot pipeline as every other catalog source.</li>
 *   <li><strong>Resilient.</strong> A feed that throws is caught and logged; the next feed still runs.</li>
 * </ul>
 *
 * <p>By default there are no configured feeds, so this importer logs a one-line "skipped" per
 * feed-required retailer and imports nothing.</p>
 */
@Component
@Order(110)
public class RetailerFeedImporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RetailerFeedImporter.class);

    private final List<RetailerFeed> feeds;
    private final RetailerSnapshotImportService snapshotImportService;

    public RetailerFeedImporter(List<RetailerFeed> feeds, RetailerSnapshotImportService snapshotImportService) {
        this.feeds = feeds == null ? List.of() : feeds;
        this.snapshotImportService = snapshotImportService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<FeedResult> results = importConfiguredFeeds();
        long configured = results.stream().filter(result -> result.status() != Status.SKIPPED_NOT_CONFIGURED).count();
        log.info("Retailer feed import: done (feeds={}, configured={}, results={}).", feeds.size(), configured, results);
    }

    /**
     * Imports every configured feed; skips the rest cleanly. Never throws — each feed's outcome is
     * captured in the returned list so callers (and tests) can assert behaviour without reading logs.
     */
    public List<FeedResult> importConfiguredFeeds() {
        List<FeedResult> results = new ArrayList<>();
        for (RetailerFeed feed : feeds) {
            String retailer = feed.retailer();
            if (!feed.isConfigured()) {
                log.info("Retailer feed import: preskačem {} — {}", retailer, feed.statusReason());
                results.add(new FeedResult(retailer, Status.SKIPPED_NOT_CONFIGURED, 0, feed.statusReason()));
                continue;
            }
            try {
                List<RetailerProductSnapshotDto> rows = feed.fetchSnapshot();
                if (rows == null || rows.isEmpty()) {
                    log.info("Retailer feed import: {} je konfiguriran ali nije vratio nijedan redak — ništa za uvoz.", retailer);
                    results.add(new FeedResult(retailer, Status.CONFIGURED_EMPTY, 0, "Feed je vratio 0 redaka."));
                    continue;
                }
                ImportSummaryDto summary = snapshotImportService.importSnapshot(rows);
                int imported = summary.created() + summary.updated();
                log.info("Retailer feed import: {} uvezeno (received={}, created={}, updated={}, skipped={}, errors={}).",
                        retailer, summary.totalReceived(), summary.created(), summary.updated(), summary.skipped(), summary.errors().size());
                results.add(new FeedResult(retailer, Status.IMPORTED, imported, "OK"));
            } catch (Exception exception) {
                // A broken feed must not take the app (or the other feeds) down.
                log.error("Retailer feed import: {} nije uspio — preskačem i nastavljam.", retailer, exception);
                results.add(new FeedResult(retailer, Status.ERROR, 0, exception.getMessage()));
            }
        }
        return results;
    }

    public enum Status {
        /** No feed URL/credentials supplied — cleanly skipped. */
        SKIPPED_NOT_CONFIGURED,
        /** Configured but the feed returned no rows. */
        CONFIGURED_EMPTY,
        /** Configured and rows were imported through the validated pipeline. */
        IMPORTED,
        /** Configured but the feed threw; caught and logged. */
        ERROR
    }

    public record FeedResult(String retailer, Status status, int importedCount, String message) {
    }
}
