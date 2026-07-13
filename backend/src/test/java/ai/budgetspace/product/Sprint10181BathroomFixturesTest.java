package ai.budgetspace.product;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlanItemDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.dto.ReplaceProductRequest;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.dto.SimilarItemsRequest;
import ai.budgetspace.dto.SimilarItemsResponse;
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
 * Sprint 10.181 (Phase 2) — real bathroom FIXTURES (toilets / bathtubs / showers) for the EU markets, from a local
 * sanitary-ware specialist per market (the pieces IKEA/JYSK don't sell). Proves the new resource imports cleanly, every
 * row is a valid market-scoped planner-eligible fixture with a real URL + image + unique id, bathtubs and showers are
 * distinguishable, the fully-covered markets meet the 6/4/4 target, and the fixtures actually reach a plan with the
 * shower/bathtub subtype honored (incl. Similar Items + Replace Product).
 */
class Sprint10181BathroomFixturesTest {

    private static final String RESOURCE = "/catalog/real-eu-bathroom-fixtures-10-181.json";
    private static final Set<String> FIXTURE_CATS = Set.of("toilet", "bathtub", "shower");
    // Markets that met the full 6 toilet / 4 bathtub / 4 shower target (FR/ES/AT/FI partial, NL thin — documented).
    private static final List<String> FULLY_COVERED = List.of("DE", "IT", "SI", "SK", "PT", "NO", "SE");

    @Test
    void everyFixtureRowIsValidAndPlannerEligible() throws Exception {
        List<Product> saved = importResource(RESOURCE);
        assertThat(saved).as("bathroom fixtures resource is non-empty").isNotEmpty();
        assertThat(saved).allSatisfy(p -> {
            assertThat(p.getCategory()).as("category %s", p.getExternalId()).isIn(FIXTURE_CATS);
            assertThat(p.getRoomTags()).as("bathroom tag %s", p.getExternalId()).contains("bathroom");
            assertThat(Markets.isKnown(p.getMarket())).as("known market %s (%s)", p.getExternalId(), p.getMarket()).isTrue();
            assertThat(p.getPrice().signum()).as("price>0 %s", p.getExternalId()).isPositive();
            assertThat(p.getProductUrl()).as("http url %s", p.getExternalId()).startsWith("http");
            assertThat(p.getImageUrl()).as("http image %s", p.getExternalId()).startsWith("http");
            assertThat(p.isImageVerified()).as("imageVerified %s", p.getExternalId()).isTrue();
            assertThat(CatalogSourcePolicy.isPlannerEligible(p))
                    .as("planner-eligible %s", p.getExternalId()).isTrue();
        });
    }

    @Test
    void externalIdsAndProductUrlsAreUnique() throws Exception {
        List<RetailerProductSnapshotDto> raw = loadRaw(RESOURCE);
        assertThat(raw.stream().map(RetailerProductSnapshotDto::externalId).distinct().count())
                .as("unique externalIds").isEqualTo(raw.size());
        assertThat(raw.stream().map(r -> r.productUrl().toLowerCase()).distinct().count())
                .as("unique productUrls").isEqualTo(raw.size());
    }

    @Test
    void bathtubsAndShowersAreDistinguishable() throws Exception {
        List<Product> saved = importResource(RESOURCE);
        List<Product> tubs = saved.stream().filter(p -> "bathtub".equals(p.getCategory())).toList();
        List<Product> showers = saved.stream().filter(p -> "shower".equals(p.getCategory())).toList();
        assertThat(tubs).isNotEmpty();
        assertThat(showers).isNotEmpty();
        // A product filed under "bathtub" always presents the bathtub facet, one under "shower" the shower facet
        // (a combined shower-bath may legitimately present BOTH — the stored category is authoritative).
        assertThat(tubs).allSatisfy(p ->
                assertThat(ProductTaxonomy.isBathtubFixture(p.getCategory(), p.getName())).isTrue());
        assertThat(showers).allSatisfy(p ->
                assertThat(ProductTaxonomy.isShowerFixture(p.getCategory(), p.getName())).isTrue());
    }

    @Test
    void fullyCoveredMarketsMeetTheTarget() throws Exception {
        List<Product> saved = importResource(RESOURCE);
        for (String market : FULLY_COVERED) {
            Map<String, Long> byCat = saved.stream().filter(p -> market.equals(p.getMarket()))
                    .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));
            assertThat(byCat.getOrDefault("toilet", 0L)).as("%s toilets", market).isGreaterThanOrEqualTo(6L);
            assertThat(byCat.getOrDefault("bathtub", 0L)).as("%s bathtubs", market).isGreaterThanOrEqualTo(4L);
            assertThat(byCat.getOrDefault("shower", 0L)).as("%s showers", market).isGreaterThanOrEqualTo(4L);
        }
    }

    // ---- planner-level: the fixtures reach a plan, and the shower/bathtub subtype is honored ----

    @Test
    void siBathroomPlanOffersRealFixtures() throws Exception {
        PlannerService planner = plannerFromWholeCatalog();
        List<String> cats = planCategories(planner.generateResolved(bathroomInput("SI", 3000, List.of(), List.of())));
        assertThat(cats).as("SI bathroom plan now offers a toilet").contains("toilet");
        // a wet fixture (bathtub or shower) is in the plan too
        assertThat(cats).as("SI bathroom plan offers a bathtub or shower").containsAnyOf("bathtub", "shower", "bath-shower");
    }

    @Test
    void explicitShowerRequestReturnsAShowerNotABathtub() throws Exception {
        PlannerService planner = plannerFromWholeCatalog();
        FurnishingPlanDto plan = planner.generateResolved(bathroomInput("SI", 3000, List.of("shower"), List.of())).plans().get(0);
        PlanItemDto fixture = wetFixture(plan);
        assertThat(fixture).as("a wet fixture is in the SI shower plan").isNotNull();
        assertThat(ProductTaxonomy.isShowerFixture(fixture.product().category(), fixture.product().name()))
                .as("shower request -> shower: %s", fixture.product().name()).isTrue();
        assertThat(ProductTaxonomy.isBathtubFixture(fixture.product().category(), fixture.product().name()))
                .as("...not a pure bathtub").isFalse();
    }

    @Test
    void explicitBathtubRequestReturnsABathtub() throws Exception {
        PlannerService planner = plannerFromWholeCatalog();
        FurnishingPlanDto plan = planner.generateResolved(bathroomInput("SI", 3000, List.of("bathtub"), List.of())).plans().get(0);
        PlanItemDto fixture = wetFixture(plan);
        assertThat(fixture).as("a wet fixture is in the SI bathtub plan").isNotNull();
        assertThat(ProductTaxonomy.isBathtubFixture(fixture.product().category(), fixture.product().name()))
                .as("bathtub request -> bathtub: %s", fixture.product().name()).isTrue();
    }

    @Test
    void findSimilarForAShowerStaysWithinShowersAndMarket() throws Exception {
        List<Product> all = importWholeCatalog();
        Product shower = all.stream()
                .filter(p -> "SE".equals(p.getMarket()) && "shower".equals(p.getCategory()))
                .filter(CatalogSourcePolicy::isPlannerEligible).findFirst().orElseThrow();
        PlannerService planner = plannerFromCatalog(all);
        SimilarItemsResponse res = planner.findSimilar(
                new SimilarItemsRequest(ProductDto.from(shower), bathroomInput("SE", 40000, List.of(), List.of()), 60000));
        for (ProductDto p : java.util.Arrays.asList(res.bestValue(), res.budgetPick(), res.nicer())) {
            if (p == null) continue;
            assertThat(ProductTaxonomy.isShowerFixture(p.category(), p.name()))
                    .as("a shower's similar items keep the shower subtype: %s", p.name()).isTrue();
            assertThat(p.market()).as("same market").isEqualTo("SE");
        }
    }

    @Test
    void replacingABathtubReturnsAnotherBathtub() throws Exception {
        PlannerService planner = plannerFromWholeCatalog();
        FurnishingPlanDto plan = planner.generateResolved(bathroomInput("NO", 40000, List.of("bathtub"), List.of())).plans().get(0);
        PlanItemDto tub = wetFixture(plan);
        assertThat(tub).isNotNull();
        FurnishingPlanDto replaced = planner.replaceProduct(new ReplaceProductRequest(
                plan, bathroomInput("NO", 40000, List.of("bathtub"), List.of()), tub.product().id(), "different"));
        PlanItemDto swapped = wetFixture(replaced);
        assertThat(swapped).isNotNull();
        assertThat(ProductTaxonomy.isBathtubFixture(swapped.product().category(), swapped.product().name()))
                .as("replacing a bathtub keeps the bathtub subtype: %s", swapped.product().name()).isTrue();
    }

    // ---- helpers ----

    private static PlanItemDto wetFixture(FurnishingPlanDto plan) {
        return plan.items().stream()
                .filter(i -> Set.of("bathtub", "shower", "bath-shower").contains(i.product().category()))
                .findFirst().orElse(null);
    }

    private static List<String> planCategories(PlanGenerationResponse response) {
        return response.plans().stream().flatMap(p -> p.items().stream())
                .map(i -> i.product().category()).collect(Collectors.toList());
    }

    private static PlannerInputDto bathroomInput(String market, int budget, List<String> mustHave, List<String> alreadyHave) {
        return new PlannerInputDto("", budget, "bathroom", "modern", "", 8, "multi", List.of(),
                "best-value", "comfort", mustHave, alreadyHave, List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), market);
    }

    private PlannerService plannerFromWholeCatalog() throws Exception { return plannerFromCatalog(importWholeCatalog()); }

    private PlannerService plannerFromCatalog(List<Product> all) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(all);
        return new PlannerService(repository);
    }

    private static List<RetailerProductSnapshotDto> loadRaw(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = Sprint10181BathroomFixturesTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("resource %s", resource).isNotNull();
            return mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {});
        }
    }

    private static List<Product> importResource(String resource) throws Exception { return importResources(List.of(resource)); }
    private static List<Product> importWholeCatalog() throws Exception { return importResources(RealCatalogSeeder.snapshotResources()); }

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
            try (InputStream in = Sprint10181BathroomFixturesTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        ImportSummaryDto summary = importer.importSnapshot(all);
        assertThat(summary.errors()).as("import errors for %s", resources).isEmpty();
        return saved;
    }
}
