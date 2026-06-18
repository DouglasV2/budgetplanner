package ai.budgetspace.feed;

import ai.budgetspace.product.CatalogSourcePolicy;
import ai.budgetspace.product.MarketplaceListingFilter;
import ai.budgetspace.product.RetailerSnapshotImportService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Sprint 10.49 — the second-hand marketplace seam. Proves the per-country marketplace placeholders are
 * registered as feed-required (never scraped), the default {@link MarketplaceFeed} ships unconfigured and
 * imports nothing (so the app runs identically with no feed), and the availability guard drops
 * sold/reserved/expired listings before anything could be imported.
 */
class MarketplaceFeedSeamTest {

    private static final List<String> MARKETPLACES = List.of(
            "Njuškalo", "Facebook Marketplace", "eBay", "Bolha", "Willhaben", "Kleinanzeigen", "Subito",
            "Tori", "Leboncoin", "Marktplaats", "Bazoš", "Wallapop", "OLX", "Finn", "Blocket", "DBA");

    @Test
    void everyMarketplaceIsRegisteredAsAFeedRequiredPlaceholder() {
        for (String marketplace : MARKETPLACES) {
            assertThat(CatalogSourcePolicy.statusFor(marketplace)).as("%s status", marketplace)
                    .isEqualTo(CatalogSourcePolicy.SourcingStatus.OFFICIAL_FEED_REQUIRED);
            assertThat(CatalogSourcePolicy.isFeedRequired(marketplace)).as("%s feed-required", marketplace).isTrue();
            assertThat(CatalogSourcePolicy.isDirectFetchAllowed(marketplace)).as("%s direct fetch", marketplace).isFalse();
        }
        assertThat(CatalogSourcePolicy.feedRequiredRetailers()).contains("Njuškalo", "eBay", "Wallapop", "Finn");
    }

    @Test
    void marketplaceConfigWiresOneUnconfiguredPlaceholderPerMarketplace() {
        MarketplaceFeedProperties properties = new MarketplaceFeedProperties(new StandardEnvironment());
        MarketplaceFeedConfig config = new MarketplaceFeedConfig();
        // Sprint 10.51: eBay is now the real EbayBrowseFeed (its own env-backed properties); unconfigured here
        // → still dormant, so it satisfies the same placeholder contract as the rest.
        EbayBrowseFeedProperties ebayProperties = new EbayBrowseFeedProperties(new StandardEnvironment());
        List<RetailerFeed> feeds = List.of(
                config.njuskaloMarketplaceFeed(properties), config.facebookMarketplaceFeed(properties),
                config.ebayMarketplaceFeed(ebayProperties), config.bolhaMarketplaceFeed(properties),
                config.willhabenMarketplaceFeed(properties), config.kleinanzeigenMarketplaceFeed(properties),
                config.subitoMarketplaceFeed(properties), config.toriMarketplaceFeed(properties),
                config.leboncoinMarketplaceFeed(properties), config.marktplaatsMarketplaceFeed(properties),
                config.bazosMarketplaceFeed(properties), config.wallapopMarketplaceFeed(properties),
                config.olxMarketplaceFeed(properties), config.finnMarketplaceFeed(properties),
                config.blocketMarketplaceFeed(properties), config.dbaMarketplaceFeed(properties));

        assertThat(feeds).hasSameSizeAs(MARKETPLACES);
        assertThat(feeds).allSatisfy(feed -> {
            assertThat(feed.sourceType()).isEqualTo(CatalogSourcePolicy.SOURCE_MARKETPLACE_LISTING);
            assertThat(feed.isConfigured()).as("%s unconfigured by default", feed.retailer()).isFalse();
            assertThat(feed.fetchSnapshot()).as("%s imports nothing", feed.retailer()).isEmpty();
        });

        // The standard importer consumes them and skips every one cleanly — the import service is never touched.
        RetailerSnapshotImportService importService = mock(RetailerSnapshotImportService.class);
        List<RetailerFeedImporter.FeedResult> results =
                new RetailerFeedImporter(feeds, importService).importConfiguredFeeds();
        assertThat(results).hasSameSizeAs(MARKETPLACES)
                .allSatisfy(r -> assertThat(r.status()).isEqualTo(RetailerFeedImporter.Status.SKIPPED_NOT_CONFIGURED));
        verifyNoInteractions(importService);
    }

    @Test
    void availabilityGuardDropsSoldReservedAndExpiredListings() {
        Instant now = Instant.parse("2026-06-18T12:00:00Z");
        assertThat(MarketplaceListingFilter.shouldDrop("Trosjed KIVIK — PRODANO", "active", "2026-06-18", now)).isTrue();
        assertThat(MarketplaceListingFilter.shouldDrop("Trosjed", "rezervirano", "2026-06-18", now)).isTrue();
        assertThat(MarketplaceListingFilter.shouldDrop("Trosjed", "aktivan", "2026-06-10", now)).isTrue(); // >24h stale
        assertThat(MarketplaceListingFilter.shouldDrop("Trosjed KIVIK siva", "aktivan", "2026-06-18T11:00:00Z", now)).isFalse();
    }
}
