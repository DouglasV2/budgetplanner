package ai.budgetspace.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.14 — the sourcing policy. Proves the architectural rule in code:
 * <ul>
 *   <li>Decathlon / Pevex / Lesnina are OFFICIAL_FEED_REQUIRED (never scraped),</li>
 *   <li>IKEA / JYSK are DIRECT_VERIFIED, Emmezeta is MANUAL_VERIFIED_ONLY,</li>
 *   <li>the production/verified-catalog gate excludes NEEDS_REVIEW, STALE and sample products, and</li>
 *   <li>the new explicit import-source provenance values are accepted.</li>
 * </ul>
 */
class CatalogSourcePolicyTest {

    @Test
    void feedRequiredRetailersAreBlockedFromDirectFetch() {
        for (String retailer : new String[] {"Decathlon", "Pevex", "Lesnina"}) {
            assertThat(CatalogSourcePolicy.statusFor(retailer))
                    .as("%s status", retailer)
                    .isEqualTo(CatalogSourcePolicy.SourcingStatus.OFFICIAL_FEED_REQUIRED);
            assertThat(CatalogSourcePolicy.isFeedRequired(retailer)).as("%s feed-required", retailer).isTrue();
            assertThat(CatalogSourcePolicy.isDirectFetchAllowed(retailer)).as("%s direct fetch", retailer).isFalse();
            assertThat(CatalogSourcePolicy.reasonFor(retailer)).contains("403");
        }
        // The original three are always feed-required; sprint 10.16 added more blocked retailers.
        assertThat(CatalogSourcePolicy.feedRequiredRetailers())
                .contains("Decathlon", "Lesnina", "Pevex")
                .doesNotContain("IKEA", "JYSK", "Emmezeta", "Harvey Norman", "Otto");
        // Sprint 10.45: Finland's Sotka renders prices client-side (JS-only) → feed-required, so FI has no
        // non-IKEA/JYSK catalog. (JS-only, not a 403, so it is asserted apart from the 403-reason loop above.)
        assertThat(CatalogSourcePolicy.statusFor("Sotka"))
                .isEqualTo(CatalogSourcePolicy.SourcingStatus.OFFICIAL_FEED_REQUIRED);
        assertThat(CatalogSourcePolicy.isFeedRequired("Sotka")).isTrue();
        assertThat(CatalogSourcePolicy.feedRequiredRetailers()).contains("Sotka");
    }

    @Test
    void verifiedRetailersAllowDirectOrManualSourcing() {
        assertThat(CatalogSourcePolicy.statusFor("IKEA")).isEqualTo(CatalogSourcePolicy.SourcingStatus.DIRECT_VERIFIED);
        assertThat(CatalogSourcePolicy.statusFor("JYSK")).isEqualTo(CatalogSourcePolicy.SourcingStatus.DIRECT_VERIFIED);
        assertThat(CatalogSourcePolicy.isDirectFetchAllowed("IKEA")).isTrue();
        assertThat(CatalogSourcePolicy.statusFor("Emmezeta"))
                .isEqualTo(CatalogSourcePolicy.SourcingStatus.MANUAL_VERIFIED_ONLY);
        assertThat(CatalogSourcePolicy.isFeedRequired("Emmezeta")).isFalse();
        assertThat(CatalogSourcePolicy.isDirectFetchAllowed("Emmezeta")).isFalse();
        // Sprint 10.45: depth — Moviflor (PT) + Nábytok (SK) are manually verified (link-out, have products).
        for (String retailer : new String[] {"Moviflor", "Nábytok"}) {
            assertThat(CatalogSourcePolicy.statusFor(retailer)).as("%s status", retailer)
                    .isEqualTo(CatalogSourcePolicy.SourcingStatus.MANUAL_VERIFIED_ONLY);
            assertThat(CatalogSourcePolicy.isFeedRequired(retailer)).as("%s feed-required", retailer).isFalse();
        }
    }

    @Test
    void unknownRetailerDefaultsToFeedRequiredSoWeNeverFetchUnvetted() {
        assertThat(CatalogSourcePolicy.isFeedRequired("Totally Unknown Store")).isTrue();
        assertThat(CatalogSourcePolicy.isDirectFetchAllowed("Totally Unknown Store")).isFalse();
    }

    @Test
    void newImportSourceProvenanceValuesAreAccepted() {
        assertThat(ProductTaxonomy.isSupportedSourceType("manual-verified")).isTrue();
        assertThat(ProductTaxonomy.isSupportedSourceType("public-product-page")).isTrue();
        assertThat(ProductTaxonomy.isSupportedSourceType("official-feed")).isTrue();
        assertThat(ProductTaxonomy.isSupportedSourceType("affiliate-feed")).isTrue();
        // Pre-10.14 values stay valid.
        assertThat(ProductTaxonomy.isSupportedSourceType("retailer-snapshot")).isTrue();
        assertThat(ProductTaxonomy.isSupportedSourceType("manual")).isTrue();
        // Garbage is still rejected.
        assertThat(ProductTaxonomy.isSupportedSourceType("totally-made-up")).isFalse();
        assertThat(CatalogSourcePolicy.isFeedSourceType("official-feed")).isTrue();
        assertThat(CatalogSourcePolicy.isFeedSourceType("affiliate-feed")).isTrue();
        assertThat(CatalogSourcePolicy.isFeedSourceType("public-product-page")).isFalse();
    }

    @Test
    void productionVerifiedGateAcceptsAFreshVerifiedCatalogProduct() {
        Product verified = baseProduct();
        verified.setSourceType(CatalogSourcePolicy.SOURCE_PUBLIC_PRODUCT_PAGE);
        verified.setSourceReference("ikea-si-10-14");
        verified.setLastCheckedAt(LocalDate.now().toString());
        assertThat(CatalogSourcePolicy.isProductionVerified(verified)).isTrue();
    }

    @Test
    void productionVerifiedGateRejectsNeedsReviewStaleAndSampleProducts() {
        // NEEDS_REVIEW
        Product needsReview = baseProduct();
        needsReview.setSourceReference("ref");
        needsReview.setLastCheckedAt(LocalDate.now().toString());
        needsReview.setDataQuality("needs-review");
        assertThat(CatalogSourcePolicy.isProductionVerified(needsReview)).as("needs-review excluded").isFalse();

        // STALE (last checked long ago)
        Product stale = baseProduct();
        stale.setSourceReference("ref");
        stale.setLastCheckedAt("2020-01-01");
        assertThat(CatalogSourcePolicy.isProductionVerified(stale)).as("stale excluded").isFalse();

        // STALE (never checked)
        Product neverChecked = baseProduct();
        neverChecked.setSourceReference("ref");
        neverChecked.setLastCheckedAt(null);
        assertThat(CatalogSourcePolicy.isProductionVerified(neverChecked)).as("unknown freshness excluded").isFalse();

        // SAMPLE (legacy data.sql row: no sourceReference)
        Product sample = baseProduct();
        sample.setSourceReference(null);
        sample.setLastCheckedAt(LocalDate.now().toString());
        assertThat(CatalogSourcePolicy.isProductionVerified(sample)).as("sample excluded").isFalse();
    }

    @Test
    void productionVerifiedGateRejectsFeedRequiredRetailerUnlessItCameFromAFeed() {
        Product scrapedDecathlon = baseProduct();
        scrapedDecathlon.setRetailer("Decathlon");
        scrapedDecathlon.setSourceReference("ref");
        scrapedDecathlon.setLastCheckedAt(LocalDate.now().toString());
        scrapedDecathlon.setSourceType(CatalogSourcePolicy.SOURCE_MANUAL_VERIFIED);
        assertThat(CatalogSourcePolicy.isProductionVerified(scrapedDecathlon))
                .as("feed-required retailer without a feed source is not verified").isFalse();

        Product feedDecathlon = baseProduct();
        feedDecathlon.setRetailer("Decathlon");
        feedDecathlon.setSourceReference("ref");
        feedDecathlon.setLastCheckedAt(LocalDate.now().toString());
        feedDecathlon.setSourceType(CatalogSourcePolicy.SOURCE_OFFICIAL_FEED);
        assertThat(CatalogSourcePolicy.isProductionVerified(feedDecathlon))
                .as("feed-required retailer sourced via official feed is verified").isTrue();
    }

    private Product baseProduct() {
        Product product = new Product();
        product.setId("p1");
        product.setName("KIVIK sofa");
        product.setRetailer("IKEA");
        product.setCategory("sofa");
        product.setPrice(BigDecimal.valueOf(499));
        product.setRoomTags("living-room");
        product.setStyleTags("modern");
        product.setAvailabilityStatus("in-stock");
        product.setInStock(true);
        return product;
    }
}
