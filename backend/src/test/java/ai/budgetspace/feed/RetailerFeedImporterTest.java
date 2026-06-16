package ai.budgetspace.feed;

import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.product.CatalogSourcePolicy;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductImportService;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.RetailerCatalogAdapter;
import ai.budgetspace.product.RetailerSnapshotImportService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.14 — proves the official/partner feed import is safe and correct:
 * <ul>
 *   <li>a missing feed config never crashes the app (it is skipped cleanly with a reason),</li>
 *   <li>a configured feed's rows flow through the validated snapshot pipeline, and</li>
 *   <li>a feed that throws is caught so the remaining feeds still run.</li>
 * </ul>
 */
class RetailerFeedImporterTest {

    @Test
    void missingFeedConfigIsSkippedCleanlyAndNeverImports() {
        ProductRepository repository = mock(ProductRepository.class);
        RetailerFeedImporter importer = importer(repository, new FakeFeed("Decathlon", false, List.of()));

        List<RetailerFeedImporter.FeedResult> results = importer.importConfiguredFeeds();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(RetailerFeedImporter.Status.SKIPPED_NOT_CONFIGURED);
        assertThat(results.get(0).importedCount()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void configuredFeedImportsItsRowsThroughTheValidatedPipeline() {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RetailerFeedImporter importer = importer(repository,
                new FakeFeed("Decathlon", true, List.of(validRow("decathlon-feed-1"))));

        List<RetailerFeedImporter.FeedResult> results = importer.importConfiguredFeeds();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(RetailerFeedImporter.Status.IMPORTED);
        assertThat(results.get(0).importedCount()).isEqualTo(1);
        verify(repository).save(any(Product.class));
    }

    @Test
    void aFeedThatThrowsIsCaughtAndDoesNotStopTheOthers() {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RetailerFeedImporter importer = importer(repository,
                new ThrowingFeed("Pevex"),
                new FakeFeed("Decathlon", true, List.of(validRow("decathlon-feed-2"))));

        List<RetailerFeedImporter.FeedResult> results = importer.importConfiguredFeeds();

        assertThat(results).extracting(RetailerFeedImporter.FeedResult::status)
                .containsExactly(RetailerFeedImporter.Status.ERROR, RetailerFeedImporter.Status.IMPORTED);
    }

    @Test
    void defaultConfigBackedFeedIsUnconfiguredWithNoEnv() {
        RetailerFeedProperties blankProps = new RetailerFeedProperties("", "", "");
        ConfigBackedRetailerFeed feed = new ConfigBackedRetailerFeed(
                "Decathlon", CatalogSourcePolicy.SOURCE_OFFICIAL_FEED, blankProps);

        assertThat(feed.isConfigured()).isFalse();
        assertThat(feed.statusReason()).contains("Nije konfiguriran");
        assertThat(feed.fetchSnapshot()).isEmpty();
    }

    private RetailerFeedImporter importer(ProductRepository repository, RetailerFeed... feeds) {
        RetailerSnapshotImportService snapshotImportService =
                new RetailerSnapshotImportService(new ProductImportService(repository), new RetailerCatalogAdapter());
        return new RetailerFeedImporter(List.of(feeds), snapshotImportService);
    }

    private RetailerProductSnapshotDto validRow(String externalId) {
        return new RetailerProductSnapshotDto(
                externalId,
                "Domyos pad za vježbanje",
                "Decathlon",
                "gym-equipment",
                BigDecimal.valueOf(39.99),
                "https://www.decathlon.hr/p/" + externalId,
                "",
                "in-stock",
                "Provjeri dostavu prije kupnje.",
                "2026-06-16",
                List.of("home-gym"),
                List.of("modern"),
                "budget",
                CatalogSourcePolicy.SOURCE_OFFICIAL_FEED,
                "Decathlon official feed",
                "decathlon-feed",
                "partial",
                "Iz službenog feeda (test).");
    }

    private static final class FakeFeed implements RetailerFeed {
        private final String retailer;
        private final boolean configured;
        private final List<RetailerProductSnapshotDto> rows;

        private FakeFeed(String retailer, boolean configured, List<RetailerProductSnapshotDto> rows) {
            this.retailer = retailer;
            this.configured = configured;
            this.rows = rows;
        }

        @Override public String retailer() { return retailer; }
        @Override public String sourceType() { return CatalogSourcePolicy.SOURCE_OFFICIAL_FEED; }
        @Override public boolean isConfigured() { return configured; }
        @Override public String statusReason() { return configured ? "configured (test)" : "not configured (test)"; }
        @Override public List<RetailerProductSnapshotDto> fetchSnapshot() { return rows; }
    }

    private static final class ThrowingFeed implements RetailerFeed {
        private final String retailer;

        private ThrowingFeed(String retailer) {
            this.retailer = retailer;
        }

        @Override public String retailer() { return retailer; }
        @Override public String sourceType() { return CatalogSourcePolicy.SOURCE_OFFICIAL_FEED; }
        @Override public boolean isConfigured() { return true; }
        @Override public String statusReason() { return "configured (test)"; }
        @Override public List<RetailerProductSnapshotDto> fetchSnapshot() {
            throw new IllegalStateException("feed boom (test)");
        }
    }
}
