package ai.budgetspace.feed;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.planner.PlannerService;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.51 → 10.64 — the eBay Browse "Rabljeno" source, now a LIVE request-time service (never persisted).
 * Proves:
 * <ul>
 *   <li>dormant without credentials (no network, returns nothing),</li>
 *   <li>only targets markets where eBay runs a local site (never claims coverage where it has none),</li>
 *   <li>its mapping keeps verified used furniture and drops sold / unpriced / unclassifiable listings,</li>
 *   <li>and a used listing fetched live NEVER enters a plan or total, yet is surfaced in the separate block (§5).</li>
 * </ul>
 * No live internet: a fake transport replays a documented Browse fixture, so the whole live path runs offline.
 */
class EbayBrowseFeedTest {

    private EbayBrowseFeed dormantFeed() {
        return new EbayBrowseFeed(new EbayBrowseFeedProperties(new StandardEnvironment()));
    }

    @Test
    void isDormantAndReturnsNothingWithoutCredentials() {
        EbayBrowseFeed feed = dormantFeed();
        assertThat(feed.isConfigured()).isFalse();
        assertThat(feed.findUsedFurniture("DE")).isEmpty(); // no credentials → no network call, nothing returned
    }

    @Test
    void onlyTargetsMarketsWhereEbayRunsALocalSite() {
        // eBay runs local sites in these markets (incl. GB, Sprint 10.55). No HR/SI/FI/NO/SE/DK/SK/PT — eBay
        // has no local marketplace there, so we never claim coverage where it has none.
        assertThat(EbayBrowseFeedProperties.SUPPORTED_MARKETS).containsExactly("DE", "IT", "AT", "FR", "NL", "ES", "GB");
        assertThat(new EbayBrowseFeedProperties(new StandardEnvironment()).markets())
                .isEqualTo(EbayBrowseFeedProperties.SUPPORTED_MARKETS);
        // An unsupported market is never queried, even when configured.
        assertThat(configuredFeed().findUsedFurniture("HR")).isEmpty();
    }

    @Test
    void mapsUsedFurnitureAndDropsSoldUnpricedAndUnclassifiable() throws Exception {
        List<RetailerProductSnapshotDto> rows = mappedSampleRows();

        // 5 listings in → 2 kept (sofa + bed); dropped: VERKAUFT (sold guard), no-price, unclassifiable.
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.secondHand()).isTrue();
            assertThat(row.retailer()).isEqualTo("eBay");
            assertThat(row.sourceType()).isEqualTo("marketplace-listing");
            assertThat(row.market()).isEqualTo("DE");
            assertThat(row.conditionLabel()).isNotBlank();
            assertThat(row.imageUrl()).isNotBlank();
            assertThat(row.productUrl()).startsWith("https://www.ebay.de/itm/");
        });

        RetailerProductSnapshotDto sofa = row(rows, "sofa");
        assertThat(sofa.externalId()).isEqualTo("ebay-v1|1101|0");
        assertThat(sofa.conditionLabel()).isEqualTo("Used");
        assertThat(sofa.sellerLocation()).isEqualTo("Berlin, DE");
        assertThat(sofa.roomTags()).contains("living-room");

        assertThat(row(rows, "bed").roomTags()).containsExactly("bedroom");
        assertThat(rows).extracting(RetailerProductSnapshotDto::externalId)
                .doesNotContain("ebay-v1|1103|0", "ebay-v1|1104|0", "ebay-v1|1105|0");
    }

    @Test
    void liveUsedListingsNeverEnterAPlanOrTotalButAreSurfacedSeparately() {
        // The catalog holds only new-retail products; the used items come LIVE from eBay (transient, never saved).
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(new ArrayList<>(List.of(
                retailSofa("ikea-de-sofa-1", "Sofa STOCKHOLM", 349),
                retailSofa("ikea-de-sofa-2", "Sofa EKTORP", 299))));
        PlannerService planner = new PlannerService(repository, configuredFeed());

        PlanGenerationResponse response = planner.generateResolved(livingRoomInput().withMarket("DE"));

        // §5 invariant: not one used listing is in any plan (so it can never be in any total).
        for (FurnishingPlanDto plan : response.plans()) {
            assertThat(plan.items()).allSatisfy(item -> {
                assertThat(item.product().secondHand()).as("plan item is new retail, not used").isFalse();
                assertThat(item.product().retailer()).isNotEqualTo("eBay");
            });
        }

        // …but the matching used listing is surfaced in the separate "Rabljeno" block.
        assertThat(response.secondHandSuggestions()).isNotEmpty();
        assertThat(response.secondHandSuggestions()).allSatisfy(product -> {
            assertThat(product.secondHand()).isTrue();
            assertThat(product.conditionLabel()).isNotBlank();
        });
        assertThat(response.secondHandSuggestions()).extracting(ProductDto::externalId)
                .contains("ebay-v1|1101|0")       // the DE living-room sofa
                .doesNotContain("ebay-v1|1102|0"); // the bed is bedroom, not this request's room
    }

    // --- helpers ---

    /** A configured eBay service whose transport replays the documented Browse fixture (no live internet). */
    private EbayBrowseFeed configuredFeed() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("ebay-test", Map.of(
                "budgetspace.marketplace-feeds.ebay.client-id", "test-app-id",
                "budgetspace.marketplace-feeds.ebay.client-secret", "test-cert-id")));
        return new EbayBrowseFeed(new EbayBrowseFeedProperties(env), fixtureTransport(), new ObjectMapper());
    }

    private EbayBrowseFeed.EbayTransport fixtureTransport() {
        return new EbayBrowseFeed.EbayTransport() {
            @Override
            public String get(String url, Map<String, String> headers) throws IOException {
                return fixtureJson(); // the Browse item_summary/search response
            }

            @Override
            public String post(String url, Map<String, String> headers, String formBody) {
                return "{\"access_token\":\"test-token\",\"expires_in\":7200}"; // the OAuth token response
            }
        };
    }

    private String fixtureJson() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/ebay/browse-de-sample.json")) {
            assertThat(in).as("eBay fixture resource").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<RetailerProductSnapshotDto> mappedSampleRows() throws Exception {
        return dormantFeed().mapSearchResponse(fixtureJson(), "DE", Instant.now());
    }

    private RetailerProductSnapshotDto row(List<RetailerProductSnapshotDto> rows, String category) {
        return rows.stream().filter(r -> category.equals(r.category())).findFirst().orElseThrow();
    }

    private Product retailSofa(String id, String name, double price) {
        Product product = new Product();
        product.setId(id);
        product.setExternalId(id);
        product.setName(name);
        product.setRetailer("IKEA");
        product.setCategory("sofa");
        product.setPrice(BigDecimal.valueOf(price));
        product.setStyleTags("modern");
        product.setRoomTags("living-room");
        product.setImage("");
        product.setImageUrl("");
        product.setUrl("https://www.ikea.com/de/de/p/" + id + "/");
        product.setProductUrl("https://www.ikea.com/de/de/p/" + id + "/");
        product.setAvailabilityStatus("in-stock");
        product.setInStock(true);
        product.setDeliveryNote("Provjeri prije kupnje.");
        product.setLastCheckedAt("2026-06-18");
        product.setPriceTier("standard");
        product.setSourceType("public-product-page");
        product.setSourceReference("sprint-10.51-ebay-test-retail");
        product.setMarket("DE");
        product.setRating(4.2);
        product.setNote("");
        return product;
    }

    private PlannerInputDto livingRoomInput() {
        return new PlannerInputDto("Dnevni boravak, moderno, do 1500 €", 1500, "living-room", "modern", "Berlin", 24,
                "multi", List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }
}
