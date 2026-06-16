package ai.budgetspace.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.21 — second-hand marketplaces are registered and feed-required (never scraped), and the
 * {@code marketplace-listing} provenance is a recognised feed-delivered source type. Mirrors the
 * design in docs/marketplace-sourcing.md §2–§4 / §8.
 */
class MarketplaceSourcingPolicyTest {

    @Test
    void marketplacesAreSupportedButFeedRequired() {
        for (String m : new String[] {"Njuškalo", "Facebook Marketplace"}) {
            assertThat(ProductTaxonomy.normalizeRetailer(m)).as("supported %s", m).isPresent();
            assertThat(CatalogSourcePolicy.statusFor(m))
                    .as("%s", m).isEqualTo(CatalogSourcePolicy.SourcingStatus.OFFICIAL_FEED_REQUIRED);
            assertThat(CatalogSourcePolicy.isFeedRequired(m)).isTrue();
            assertThat(CatalogSourcePolicy.isDirectFetchAllowed(m)).isFalse();
            assertThat(CatalogSourcePolicy.feedRequiredRetailers()).contains(m);
        }
    }

    @Test
    void marketplaceListingIsAFeedDeliveredSourceType() {
        assertThat(ProductTaxonomy.isSupportedSourceType("marketplace-listing")).isTrue();
        assertThat(CatalogSourcePolicy.isFeedSourceType(CatalogSourcePolicy.SOURCE_MARKETPLACE_LISTING)).isTrue();
        // A retail public-product-page is NOT a feed source.
        assertThat(CatalogSourcePolicy.isFeedSourceType(CatalogSourcePolicy.SOURCE_PUBLIC_PRODUCT_PAGE)).isFalse();
    }

    @Test
    void marketplaceListingFromFeedIsVerifiedButScrapedIsNot() {
        // A used listing delivered via the compliant feed (marketplace-listing) passes the verified gate.
        assertThat(CatalogSourcePolicy.isProductionVerified(marketplaceProduct("marketplace-listing"))).isTrue();
        // The same feed-required retailer must NOT be accepted from a non-feed (scraped) source type.
        assertThat(CatalogSourcePolicy.isProductionVerified(marketplaceProduct("public-product-page"))).isFalse();
    }

    private Product marketplaceProduct(String sourceType) {
        Product p = new Product();
        p.setId("njuskalo-test-1");
        p.setExternalId("njuskalo-test-1");
        p.setName("Rabljeni trosjed, Zagreb");
        p.setRetailer("Njuškalo");
        p.setCategory("sofa");
        p.setPrice(new BigDecimal("150.00"));
        p.setRoomTags("living-room");
        p.setStyleTags("modern");
        p.setInStock(true);
        p.setAvailabilityStatus("in-stock");
        p.setDataQuality("partial");
        p.setLastCheckedAt(LocalDate.now().toString());
        p.setSourceReference("njuskalo-feed-pilot");
        p.setSourceType(sourceType);
        p.setSecondHand(true);
        p.setConditionLabel("used-good");
        p.setSellerLocation("Zagreb");
        return p;
    }
}
