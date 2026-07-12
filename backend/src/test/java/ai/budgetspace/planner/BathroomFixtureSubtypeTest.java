package ai.budgetspace.planner;

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
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductImportService;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.ProductTaxonomy;
import ai.budgetspace.product.RealCatalogSeeder;
import ai.budgetspace.product.RetailerCatalogAdapter;
import ai.budgetspace.product.RetailerSnapshotImportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.181 (Phase 3 + Phase 9) — bathtub vs shower are now told apart. bath-shower stays the stored/umbrella
 * category (backwards compatible); an explicit "želim kadu" / "treba mi tuš" filters the fixture slot by the product's
 * derived facet, and Similar Items / Replace Product preserve the subtype. HR has both real bathtubs and showers
 * (Pevex), so the semantics are asserted end-to-end there.
 */
class BathroomFixtureSubtypeTest {

    // ---- taxonomy classifier ----

    @Test
    void classifierTellsBathtubsFromShowers() {
        assertThat(ProductTaxonomy.isBathtubFixture("bath-shower", "Kada Kolpa San String 170x70")).isTrue();
        assertThat(ProductTaxonomy.isShowerFixture("bath-shower", "Kada Kolpa San String 170x70")).isFalse();
        assertThat(ProductTaxonomy.isShowerFixture("bath-shower", "Tuš kabina Aquaform 90x90")).isTrue();
        assertThat(ProductTaxonomy.isBathtubFixture("bath-shower", "Tuš kabina Aquaform 90x90")).isFalse();
        // a genuine shower-bath reads as BOTH (satisfies either request, excluded by "bez kade")
        assertThat(ProductTaxonomy.isBathtubFixture("bath-shower", "Shower Bath 1700mm with screen")).isTrue();
        assertThat(ProductTaxonomy.isShowerFixture("bath-shower", "Shower Bath 1700mm with screen")).isTrue();
        // explicit categories always classify
        assertThat(ProductTaxonomy.isBathtubFixture("bathtub", "whatever")).isTrue();
        assertThat(ProductTaxonomy.isShowerFixture("shower", "whatever")).isTrue();
        // non-fixtures never do
        assertThat(ProductTaxonomy.isBathtubFixture("washbasin", "Umivaonik")).isFalse();
        assertThat(ProductTaxonomy.isShowerFixture("toilet", "WC školjka")).isFalse();
    }

    // ---- parser intent (Phase 9 parser side) ----

    private final PlannerIntentExtractor ex = new PlannerIntentExtractor();

    private PlannerInputDto parse(String prompt) {
        PlannerInputDto in = new PlannerInputDto(prompt, 0, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), "HR");
        return ex.enrich(in);
    }

    @Test
    void parserRecognizesBathroomFixtures() {
        assertThat(parse("treba mi WC školjka").mustHaveCategories()).contains("toilet");
        assertThat(parse("trebam umivaonik").mustHaveCategories()).contains("washbasin");
        assertThat(parse("treba mi tuš").mustHaveCategories()).contains("shower").doesNotContain("bathtub");
        assertThat(parse("želim tuš kabinu").mustHaveCategories()).contains("shower");
        assertThat(parse("želim kadu").mustHaveCategories()).contains("bathtub").doesNotContain("shower");
        // a fixture word alone implies the bathroom
        assertThat(parse("treba mi tuš").roomType()).isEqualTo("bathroom");
        assertThat(parse("želim kadu").roomType()).isEqualTo("bathroom");
    }

    @Test
    void parserHonorsFixtureExclusions() {
        PlannerInputDto showerNoTub = parse("tuš, bez kade");
        assertThat(showerNoTub.alreadyHaveCategories()).contains("bathtub");
        assertThat(showerNoTub.mustHaveCategories()).doesNotContain("bathtub");

        PlannerInputDto tubNoShower = parse("kada, bez tuša");
        assertThat(tubNoShower.alreadyHaveCategories()).contains("shower");
        assertThat(tubNoShower.mustHaveCategories()).doesNotContain("shower");
    }

    @Test
    void parserHandlesTheFullBathroomPrompt() {
        PlannerInputDto r = parse("kupaona dva soma, treba školjka i tuš, kadu neću");
        assertThat(r.roomType()).isEqualTo("bathroom");
        assertThat(r.budget()).isEqualTo(2000);
        assertThat(r.mustHaveCategories()).contains("toilet", "shower").doesNotContain("bathtub");
        assertThat(r.alreadyHaveCategories()).contains("bathtub");
    }

    @Test
    void parserHandlesObjectVerbAlreadyOwned() {
        assertThat(parse("spavaća 800e, krevet imam").alreadyHaveCategories()).contains("bed");
        assertThat(parse("kauč imam, ostalo mi složi").alreadyHaveCategories()).contains("sofa");
    }

    // ---- planner-level semantics (HR has real bathtubs + showers) ----

    @Test
    void explicitShowerRequestNeverReturnsABathtub() throws Exception {
        FurnishingPlanDto plan = generate("HR", 3000, List.of("shower"), List.of());
        PlanItemDto fixture = fixtureItem(plan);
        assertThat(fixture).as("a fixture is in the plan").isNotNull();
        assertThat(ProductTaxonomy.isShowerFixture(fixture.product().category(), fixture.product().name()))
                .as("explicit shower request yields a shower: %s", fixture.product().name()).isTrue();
        assertThat(ProductTaxonomy.isBathtubFixture(fixture.product().category(), fixture.product().name()))
                .as("...and not a pure bathtub").isFalse();
    }

    @Test
    void explicitBathtubRequestNeverReturnsAShowerEnclosure() throws Exception {
        FurnishingPlanDto plan = generate("HR", 3000, List.of("bathtub"), List.of());
        PlanItemDto fixture = fixtureItem(plan);
        assertThat(fixture).isNotNull();
        assertThat(ProductTaxonomy.isBathtubFixture(fixture.product().category(), fixture.product().name()))
                .as("explicit bathtub request yields a bathtub: %s", fixture.product().name()).isTrue();
    }

    @Test
    void excludingTheBathtubYieldsAShower() throws Exception {
        // "tuš, bez kade" == shower requested + bathtub excluded
        FurnishingPlanDto plan = generate("HR", 3000, List.of("shower"), List.of("bathtub"));
        PlanItemDto fixture = fixtureItem(plan);
        assertThat(fixture).isNotNull();
        assertThat(ProductTaxonomy.isBathtubFixture(fixture.product().category(), fixture.product().name()))
                .as("excluded bathtub must not appear: %s", fixture.product().name()).isFalse();
    }

    // ---- Similar Items + Replace preserve fixture subtype ----

    @Test
    void findSimilarForABathtubReturnsBathtubs() throws Exception {
        List<Product> all = importWholeCatalog();
        Product bathtub = all.stream()
                .filter(p -> "HR".equals(p.getMarket()))
                .filter(p -> ProductTaxonomy.isBathtubFixture(p.getCategory(), p.getName()))
                .filter(p -> !ProductTaxonomy.isShowerFixture(p.getCategory(), p.getName()))
                .filter(ProductTaxonomy::canEnterPlanner)
                .findFirst().orElseThrow();
        PlannerService planner = plannerFromCatalog(all);
        SimilarItemsResponse res = planner.findSimilar(
                new SimilarItemsRequest(ProductDto.from(bathtub), bathroomInput("HR", 3000), 5000));
        for (ProductDto p : java.util.Arrays.asList(res.bestValue(), res.budgetPick(), res.nicer())) {
            if (p == null) continue;
            assertThat(ProductTaxonomy.isShowerFixture(p.category(), p.name()))
                    .as("a bathtub's similar items must not be pure showers: %s", p.name()).isFalse();
        }
    }

    @Test
    void replacingAShowerReturnsAnotherShower() throws Exception {
        FurnishingPlanDto plan = generate("HR", 3000, List.of("shower"), List.of());
        PlanItemDto shower = fixtureItem(plan);
        assertThat(shower).isNotNull();
        PlannerService planner = plannerFromWholeCatalog();
        FurnishingPlanDto replaced = planner.replaceProduct(
                new ReplaceProductRequest(plan, bathroomInput("HR", 3000, List.of("shower"), List.of()),
                        shower.product().id(), "different"));
        PlanItemDto swapped = fixtureItem(replaced);
        assertThat(swapped).isNotNull();
        assertThat(ProductTaxonomy.isBathtubFixture(swapped.product().category(), swapped.product().name()))
                .as("replacing a shower must not hand back a bathtub: %s", swapped.product().name()).isFalse();
    }

    // ---- LLM/taxonomy sync (Phase 12) ----

    @Test
    void llmSystemPromptListsEveryCanonicalCategory() {
        String prompt = PromptIntelligenceService.systemPrompt();
        for (String category : ProductTaxonomy.canonicalCategories()) {
            assertThat(prompt).as("LLM prompt must allow category '%s'", category).contains(category);
        }
        // specifically the ones that were missing before this sprint
        assertThat(prompt).contains("toilet", "washbasin", "bathtub", "shower", "oven", "fridge", "kitchen-set");
    }

    // ---- helpers ----

    private static PlanItemDto fixtureItem(FurnishingPlanDto plan) {
        return plan.items().stream()
                .filter(i -> ProductTaxonomy.isFixtureCategory(i.product().category()))
                .findFirst().orElse(null);
    }

    private FurnishingPlanDto generate(String market, int budget, List<String> mustHave, List<String> alreadyHave) throws Exception {
        PlannerService planner = plannerFromWholeCatalog();
        PlanGenerationResponse response = planner.generateResolved(bathroomInput(market, budget, mustHave, alreadyHave));
        return response.plans().get(0);
    }

    private static PlannerInputDto bathroomInput(String market, int budget) {
        return bathroomInput(market, budget, List.of(), List.of());
    }

    private static PlannerInputDto bathroomInput(String market, int budget, List<String> mustHave, List<String> alreadyHave) {
        return new PlannerInputDto("", budget, "bathroom", "modern", "", 8, "multi", List.of(),
                "best-value", "comfort", mustHave, alreadyHave, List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), market);
    }

    private PlannerService plannerFromWholeCatalog() throws Exception {
        return plannerFromCatalog(importWholeCatalog());
    }

    private PlannerService plannerFromCatalog(List<Product> all) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(all);
        return new PlannerService(repository);
    }

    private static List<Product> importWholeCatalog() throws Exception {
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
        for (String resource : RealCatalogSeeder.snapshotResources()) {
            try (InputStream in = BathroomFixtureSubtypeTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        ImportSummaryDto summary = importer.importSnapshot(all);
        assertThat(summary.errors()).as("import errors").isEmpty();
        return saved;
    }
}
