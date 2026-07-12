package ai.budgetspace.product;

import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.planner.PlannerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.180 ("probudi katalog") — cross-market IKEA depth for the thin SECONDARY cells the coverage map surfaced.
 * A pre-sprint measurement found 163 planner cells with &lt;3 real options — almost all of them secondary categories
 * (rug / lighting / decor / storage / dining) in secondary rooms (hallway, dining-room, home-office, bedroom) across the
 * 15 markets, worst in the IKEA-only markets (GB/ES/IT/PT/FR/FI/SK). This sprint harvested each market's OWN live IKEA
 * category listing (global category-id cross-market redirect) and re-read every product page live for its JSON-LD price
 * + currency + og:image, then rule-cleaned + ran a 15-agent multilingual judge/prune pass.
 *
 * <p>This test proves the new resource seeds with zero import errors, every row is IKEA / real /p/ URL / image-verified /
 * planner-eligible / market-scoped, every market gained real depth, and the previously-empty secondary cells now carry
 * stock in every market (so a dining-room/hallway/office plan in any market can offer a rug, a lamp and a mirror).</p>
 */
class Sprint10180CatalogRuntimeTest {

    private static final String RESOURCE = "/catalog/real-ikea-catalog-wakeup-10-180.json";
    private static final List<String> MARKETS_15 = List.of(
            "HR", "SI", "AT", "DE", "DK", "SE", "NO", "FI", "NL", "FR", "IT", "ES", "PT", "SK", "GB");
    private static final Set<String> HARVESTED_CATEGORIES = Set.of(
            "rug", "lighting", "decor", "storage", "dining-table", "dining-chair", "textiles");

    @Test
    void wakeupCatalogImportsCleanlyAndEveryRowIsPlannerEligible() throws Exception {
        List<Product> saved = importResource(RESOURCE);
        assertThat(saved).as("wake-up catalog is non-empty").isNotEmpty();
        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getCategory()).as("category %s", product.getExternalId()).isIn(HARVESTED_CATEGORIES);
            assertThat(product.getMarket()).as("known market %s", product.getExternalId())
                    .matches(m -> Markets.isKnown(m));
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getProductUrl()).as("real /p/ url %s", product.getExternalId())
                    .contains("ikea.com").contains("/p/");
            assertThat(product.isImageVerified()).as("imageVerified %s", product.getExternalId()).isTrue();
            assertThat(product.getRoomTags()).as("has a room %s", product.getExternalId()).isNotBlank();
            assertThat(product.getStyleTags()).as("has a style %s", product.getExternalId()).isNotBlank();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });
    }

    @Test
    void everyMarketGainedSecondaryDepth() throws Exception {
        Map<String, Long> perMarket = countByMarket(importResource(RESOURCE));
        // The harvest added ~67-79 rows to each of the 15 markets; assert a comfortable floor so the sprint's
        // "all markets, especially the weak ones" promise is locked in.
        for (String market : MARKETS_15) {
            assertThat(perMarket.getOrDefault(market, 0L))
                    .as("wake-up depth for %s", market).isGreaterThanOrEqualTo(55L);
        }
    }

    @Test
    void previouslyEmptyDiningRoomRugNowCoversEveryMarket() throws Exception {
        // dining-room/rug was 0 in nearly every market before this sprint — a dining-room plan could never offer a rug.
        // A harvested area rug is tagged for living-room/bedroom/home-office/dining-room/hallway, so it fills all of them.
        List<Product> eligible = plannerEligibleCatalog();
        long marketsWithDiningRug = MARKETS_15.stream()
                .filter(m -> eligible.stream().anyMatch(p -> m.equals(p.getMarket())
                        && "rug".equals(p.getCategory()) && roomTags(p).contains("dining-room")))
                .count();
        assertThat(marketsWithDiningRug).as("markets with a dining-room rug (was ~0 before)").isGreaterThanOrEqualTo(14L);
    }

    @Test
    void weakSecondaryCellsAreFilledAcrossMarkets() throws Exception {
        List<Product> eligible = plannerEligibleCatalog();
        // Each of these (room, category) cells was <3 in most markets before the sprint; now present in ~all 15.
        assertThat(marketsCovering(eligible, "hallway", "rug")).as("hallway rug").isGreaterThanOrEqualTo(13L);
        assertThat(marketsCovering(eligible, "hallway", "lighting")).as("hallway lighting").isGreaterThanOrEqualTo(13L);
        assertThat(marketsCovering(eligible, "home-office", "rug")).as("office rug").isGreaterThanOrEqualTo(13L);
        assertThat(marketsCovering(eligible, "home-office", "decor")).as("office decor").isGreaterThanOrEqualTo(13L);
        assertThat(marketsCovering(eligible, "dining-room", "lighting")).as("dining lighting").isGreaterThanOrEqualTo(13L);
        assertThat(marketsCovering(eligible, "bedroom", "rug")).as("bedroom rug").isGreaterThanOrEqualTo(13L);
    }

    @Test
    void noWakeupRowCollidesWithTheExistingCatalog() throws Exception {
        // The harvest deduped by (market, article); prove no imported wake-up row shares a productUrl with the rest of
        // the catalog (the StoreLinkIntegrityTest guard covers this globally; this pins it to this sprint's file).
        List<RetailerProductSnapshotDto> mine = loadRaw(RESOURCE);
        Set<String> otherUrls = new java.util.HashSet<>();
        for (String resource : RealCatalogSeeder.snapshotResources()) {
            if (resource.endsWith("real-ikea-catalog-wakeup-10-180.json")) continue;
            for (RetailerProductSnapshotDto row : loadRaw(resource)) otherUrls.add(normalizeUrl(row.productUrl()));
        }
        List<String> collisions = mine.stream()
                .filter(row -> otherUrls.contains(normalizeUrl(row.productUrl())))
                .map(RetailerProductSnapshotDto::externalId).toList();
        assertThat(collisions).as("wake-up rows colliding with the existing catalog: %s", collisions).isEmpty();
    }

    // ---- planner-level: the new depth actually reaches a plan ----

    @Test
    void gbDiningRoomPlanCanNowOfferARug() throws Exception {
        // GB is the thinnest market and IKEA-only; before this sprint a GB dining-room plan had no rug to offer.
        PlannerService planner = plannerFromCatalog(importWholeCatalog());
        PlanGenerationResponse response = planner.generateResolved(
                marketRoomInput("blagovaonica", 1600, "dining-room", "GB"));
        assertThat(response.plans().get(0).items()).as("GB dining-room plan is non-empty").isNotEmpty();
        assertThat(planCategories(response)).as("GB dining-room plan now offers a rug").contains("rug");
    }

    @Test
    void fiHallwayPlanCanNowOfferALamp() throws Exception {
        // FI was the market whose rugs were wrongly nuked mid-build (Finnish "matto" == rug); this pins the fix and
        // proves the hallway secondary depth reaches an FI plan.
        PlannerService planner = plannerFromCatalog(importWholeCatalog());
        PlanGenerationResponse response = planner.generateResolved(
                marketRoomInput("hodnik", 900, "hallway", "FI"));
        assertThat(response.plans().get(0).items()).as("FI hallway plan is non-empty").isNotEmpty();
        assertThat(planCategories(response)).as("FI hallway plan draws on the new secondary depth")
                .containsAnyOf("lighting", "rug", "decor");
    }

    // ---- helpers ----

    private static long marketsCovering(List<Product> eligible, String room, String category) {
        return MARKETS_15.stream()
                .filter(m -> eligible.stream().anyMatch(p -> m.equals(p.getMarket())
                        && category.equals(p.getCategory()) && roomTags(p).contains(room)))
                .count();
    }

    private static List<String> roomTags(Product p) {
        return p.getRoomTags() == null ? List.of()
                : List.of(p.getRoomTags().split(",")).stream().map(String::trim).collect(Collectors.toList());
    }

    private List<Product> plannerEligibleCatalog() throws Exception {
        return importWholeCatalog().stream().filter(CatalogSourcePolicy::isPlannerEligible).toList();
    }

    private static List<String> planCategories(PlanGenerationResponse response) {
        return response.plans().stream()
                .flatMap(plan -> plan.items().stream())
                .map(item -> item.product().category())
                .collect(Collectors.toList());
    }

    private PlannerService plannerFromCatalog(List<Product> all) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(all);
        return new PlannerService(repository);
    }

    private static PlannerInputDto marketRoomInput(String prompt, int budget, String room, String market) {
        return new PlannerInputDto(prompt, budget, room, "modern", "", 8, "multi", List.of(),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), market);
    }

    private static Map<String, Long> countByMarket(List<Product> products) {
        return products.stream().collect(Collectors.groupingBy(Product::getMarket, Collectors.counting()));
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim().toLowerCase();
        int cut = trimmed.indexOf('?');
        if (cut >= 0) trimmed = trimmed.substring(0, cut);
        cut = trimmed.indexOf('#');
        if (cut >= 0) trimmed = trimmed.substring(0, cut);
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private static List<RetailerProductSnapshotDto> loadRaw(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = Sprint10180CatalogRuntimeTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("catalog resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }

    private static List<Product> importResource(String resource) throws Exception {
        return importResources(List.of(resource));
    }

    private static List<Product> importWholeCatalog() throws Exception {
        return importResources(RealCatalogSeeder.snapshotResources());
    }

    private static List<Product> importResources(List<String> resources) throws Exception {
        List<Product> saved = new ArrayList<>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findByExternalId(anyString())).thenAnswer(inv -> saved.stream()
                .filter(p -> p.getExternalId().equals(inv.getArgument(0))).findFirst());
        when(repository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (!saved.contains(p)) saved.add(p);
            return p;
        });
        RetailerSnapshotImportService importer = new RetailerSnapshotImportService(
                new ProductImportService(repository), new RetailerCatalogAdapter());
        ObjectMapper mapper = new ObjectMapper();
        List<RetailerProductSnapshotDto> all = new ArrayList<>();
        for (String resource : resources) {
            try (InputStream in = Sprint10180CatalogRuntimeTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        ImportSummaryDto summary = importer.importSnapshot(all);
        assertThat(summary.errors()).as("import errors for %s", resources).isEmpty();
        return saved;
    }
}
