package ai.budgetspace.feed;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.planner.PlannerService;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductImportService;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.RetailerCatalogAdapter;
import ai.budgetspace.product.RetailerSnapshotImportService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.51 — the real eBay Browse "Rabljeno" feed and the second-hand pipeline it fills. Proves:
 * <ul>
 *   <li>the feed is dormant (no network, imports nothing) without credentials,</li>
 *   <li>it only targets markets where eBay runs a local site (never claims coverage where it has none),</li>
 *   <li>its mapping keeps verified used furniture and drops sold / unpriced / unclassifiable listings,</li>
 *   <li>and a used listing NEVER enters a plan or total, yet is surfaced in the separate block (§5).</li>
 * </ul>
 * No credentials and no live internet — the mapping runs on a fixture modelled on the documented Browse shape.
 */
class EbayBrowseFeedTest {

    private EbayBrowseFeed dormantFeed() {
        return new EbayBrowseFeed(new EbayBrowseFeedProperties(new StandardEnvironment()));
    }

    @Test
    void isDormantAndImportsNothingWithoutCredentials() {
        EbayBrowseFeed feed = dormantFeed();
        assertThat(feed.retailer()).isEqualTo("eBay");
        assertThat(feed.market()).isNull();
        assertThat(feed.sourceType()).isEqualTo("marketplace-listing");
        assertThat(feed.isConfigured()).isFalse();
        assertThat(feed.fetchSnapshot()).isEmpty(); // no credentials → no network call, nothing imported
    }

    @Test
    void onlyTargetsMarketsWhereEbayRunsALocalSite() {
        // eBay runs local sites in these markets (incl. GB, Sprint 10.55). No HR/SI/FI/NO/SE/DK/SK/PT — eBay
        // has no local marketplace there, so we never claim coverage where it has none.
        assertThat(EbayBrowseFeedProperties.SUPPORTED_MARKETS).containsExactly("DE", "IT", "AT", "FR", "NL", "ES", "GB");
        assertThat(new EbayBrowseFeedProperties(new StandardEnvironment()).markets())
                .isEqualTo(EbayBrowseFeedProperties.SUPPORTED_MARKETS);
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
    void usedListingsNeverEnterAPlanOrTotalButAreSurfacedSeparately() throws Exception {
        // Import the real eBay-mapped used rows so secondHand flows through the whole pipeline.
        List<Product> catalog = new ArrayList<>(importUsedProducts(mappedSampleRows()));
        catalog.add(retailSofa("ikea-de-sofa-1", "Sofa STOCKHOLM", 349));
        catalog.add(retailSofa("ikea-de-sofa-2", "Sofa EKTORP", 299));

        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(catalog);
        PlannerService planner = new PlannerService(repository);

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

    private List<RetailerProductSnapshotDto> mappedSampleRows() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/ebay/browse-de-sample.json")) {
            assertThat(in).as("eBay fixture resource").isNotNull();
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return dormantFeed().mapSearchResponse(json, "DE", Instant.now());
        }
    }

    private RetailerProductSnapshotDto row(List<RetailerProductSnapshotDto> rows, String category) {
        return rows.stream().filter(r -> category.equals(r.category())).findFirst().orElseThrow();
    }

    private List<Product> importUsedProducts(List<RetailerProductSnapshotDto> rows) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenReturn(Optional.empty());
        List<Product> saved = new ArrayList<>();
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            saved.add(product);
            return product;
        });
        new RetailerSnapshotImportService(new ProductImportService(repository), new RetailerCatalogAdapter())
                .importSnapshot(rows);
        return saved;
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
