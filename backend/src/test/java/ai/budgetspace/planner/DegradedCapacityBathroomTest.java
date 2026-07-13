package ai.budgetspace.planner;

import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlanItemDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.product.Markets;
import ai.budgetspace.product.Product;
import ai.budgetspace.product.ProductImportService;
import ai.budgetspace.product.ProductRepository;
import ai.budgetspace.product.ProductTaxonomy;
import ai.budgetspace.product.RealCatalogSeeder;
import ai.budgetspace.product.RetailerCatalogAdapter;
import ai.budgetspace.product.RetailerSnapshotImportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
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
 * Sprint 10.181 — generic catalog-capacity / degraded behaviour.
 *
 * <p>When a user EXPLICITLY asks for a category the selected market's catalog cannot supply, the planner must be
 * HONEST about it: mark the plan partial and name the gap, instead of (a) inventing a product, (b) borrowing another
 * market's product / currency, (c) swapping a bathtub for a shower or vice-versa, or (d) silently dropping the
 * request and returning a "complete-looking" plan. The behaviour is derived from real catalog capacity, never a
 * per-market {@code if} — so it holds for every market and every category.</p>
 *
 * <p>Netherlands (NL) is the sharp case: its bathroom catalog has washbasins/storage/mirrors/lighting/textiles but
 * <b>0 toilets and 0 bathtubs</b> (Saniweb blocks listings) and exactly <b>1 shower</b>. The available categories
 * still plan normally; the unavailable ones are surfaced. Ground truth is asserted against the WHOLE real catalog.</p>
 */
class DegradedCapacityBathroomTest {

    private static PlannerService planner;

    @BeforeAll
    static void importCatalogOnce() throws Exception {
        planner = plannerFromCatalog(importWholeCatalog());
    }

    // ---------- NL: explicitly requested fixtures the market cannot supply ----------

    @Test
    void nlExplicitToilet_isFlaggedUnavailable_neverInventedOrForeign() {
        PlanGenerationResponse r = plan("NL", 3000, List.of("toilet"), List.of());

        // Honest: partial + names the toilet, and does NOT pretend the request was met.
        assertThat(r.partialPlan()).as("NL has 0 toilets → plan must be partial").isTrue();
        assertThat(r.missingImportantCategories()).contains("toilet");
        // Never invents / borrows: no toilet item at all, and every item stays on the NL market in EUR.
        assertThat(categoriesInAnyTier(r)).doesNotContain("toilet");
        assertOnlyFromMarket(r, "NL");
        // The rest of the bathroom (washbasin/storage/…) still plans — degraded, not empty.
        assertThat(primaryItems(r)).as("available categories still fill the plan").isNotEmpty();
    }

    @Test
    void nlExplicitBathtub_isFlaggedUnavailable_andNeverSwappedForTheShower() {
        PlanGenerationResponse r = plan("NL", 3000, List.of("bathtub"), List.of());

        assertThat(r.partialPlan()).as("NL has 0 bathtubs → partial").isTrue();
        assertThat(r.missingImportantCategories()).contains("bathtub");
        // The wrong-subtype guard: NL's single wet fixture is a SHOWER; a bathtub request must NOT hand it back.
        for (PlanItemDto item : allItems(r)) {
            assertThat(ProductTaxonomy.isShowerFixture(item.product().category(), item.product().name()))
                    .as("a bathtub request must not return a shower: %s", item.product().name()).isFalse();
            assertThat(ProductTaxonomy.isBathtubFixture(item.product().category(), item.product().name()))
                    .as("no invented bathtub exists in NL: %s", item.product().name()).isFalse();
        }
        assertOnlyFromMarket(r, "NL");
    }

    @Test
    void nlExplicitShower_isAvailable_soItPlansNormally_notFalselyFlagged() {
        // NL has exactly ONE shower — availability must NOT be mistaken for a limitation.
        PlanGenerationResponse r = plan("NL", 3000, List.of("shower"), List.of());

        assertThat(r.partialPlan()).as("the shower IS available → not partial").isFalse();
        assertThat(r.missingImportantCategories()).doesNotContain("shower");
        assertThat(r.catalogWarning()).isNull();
        PlanItemDto fixture = fixtureItem(r);
        assertThat(fixture).as("the NL shower is in the plan").isNotNull();
        assertThat(ProductTaxonomy.isShowerFixture(fixture.product().category(), fixture.product().name())).isTrue();
        assertThat(fixture.product().market()).isEqualTo("NL");
        assertThat(fixture.product().currency()).isEqualTo("EUR");
        assertOnlyFromMarket(r, "NL");
    }

    @Test
    void nlCompleteBathroomRenovation_flagsOnlyTheTrulyUnavailableFixtures() {
        // Full sanitary renovation: toilet + washbasin + bathtub + shower. In NL only washbasin + shower exist.
        PlanGenerationResponse r = plan("NL", 4000, List.of("toilet", "washbasin", "bathtub", "shower"), List.of());

        assertThat(r.partialPlan()).isTrue();
        assertThat(r.missingImportantCategories())
                .as("only the unavailable fixtures are flagged")
                .contains("toilet", "bathtub")
                .doesNotContain("washbasin", "shower");
        // The available fixtures ARE delivered.
        assertThat(categoriesInAnyTier(r)).contains("washbasin");
        assertThat(allItems(r).stream().anyMatch(i ->
                ProductTaxonomy.isShowerFixture(i.product().category(), i.product().name())))
                .as("the available shower is delivered").isTrue();
        // Nothing invented or foreign, and no bathtub sneaks in.
        assertThat(allItems(r).stream().anyMatch(i ->
                ProductTaxonomy.isBathtubFixture(i.product().category(), i.product().name()))).isFalse();
        assertThat(categoriesInAnyTier(r)).doesNotContain("toilet");
        assertOnlyFromMarket(r, "NL");
    }

    @Test
    void nlBathroomUsingOnlyAvailableCategories_worksWithNoFalseWarning() {
        // washbasin + storage both exist in NL → the request is fully satisfiable and must NOT be flagged.
        PlanGenerationResponse r = plan("NL", 2500, List.of("washbasin", "storage"), List.of());

        assertThat(r.partialPlan()).isFalse();
        assertThat(r.missingImportantCategories()).isEmpty();
        assertThat(r.catalogWarning()).isNull();
        assertThat(categoriesInAnyTier(r)).contains("washbasin", "storage");
        assertOnlyFromMarket(r, "NL");
    }

    // ---------- honest, human limitation message ----------

    @Test
    void honestLimitationResponse_namesTheMarketAndTheMissingFixture() {
        PlanGenerationResponse r = plan("NL", 3000, List.of("toilet"), List.of());

        assertThat(r.catalogWarning()).isNotNull();
        assertThat(r.catalogWarning())
                .as("message must be specific and honest: %s", r.catalogWarning())
                .contains("NL")                 // names the selected market
                .contains("nemamo")             // states we don't carry it
                .contains("WC školjka");        // names the requested fixture
        // It must NOT claim completeness.
        assertThat(r.catalogWarning()).doesNotContain("kompletan plan složen");
    }

    // ---------- limited (not zero) capacity must NOT be treated as unavailable ----------

    @Test
    void frToilet_limitedButAvailable_isDeliveredNotFlagged() {
        assertFixtureDelivered("FR", List.of("toilet"), "toilet");
    }

    @Test
    void esToilet_limitedButAvailable_isDeliveredNotFlagged() {
        assertFixtureDelivered("ES", List.of("toilet"), "toilet");
    }

    @Test
    void atToilet_limitedButAvailable_isDeliveredNotFlagged() {
        assertFixtureDelivered("AT", List.of("toilet"), "toilet");
    }

    @Test
    void fiBathtub_limitedButAvailable_isDeliveredNotFlagged() {
        // FI has only 3 bathtubs, but "few" is not "none": the request is honoured, not flagged.
        PlanGenerationResponse r = plan("FI", 6000, List.of("bathtub"), List.of());
        assertThat(r.missingImportantCategories()).doesNotContain("bathtub");
        PlanItemDto fixture = fixtureItem(r);
        assertThat(fixture).as("a real FI bathtub is delivered").isNotNull();
        assertThat(ProductTaxonomy.isBathtubFixture(fixture.product().category(), fixture.product().name())).isTrue();
        assertThat(fixture.product().market()).isEqualTo("FI");
        assertThat(fixture.product().currency()).isEqualTo("EUR");
    }

    // ---------- foreign-market fallback is impossible ----------

    @Test
    void unavailableRequest_neverPullsFromAnotherMarket() {
        // NL has no toilet; FR/DE/etc. do. The planner must NOT reach across the border to fill the gap.
        PlanGenerationResponse r = plan("NL", 5000, List.of("toilet", "bathtub"), List.of());
        assertThat(r.partialPlan()).isTrue();
        for (PlanItemDto item : allItems(r)) {
            assertThat(item.product().market())
                    .as("every product stays on the selected market: %s (%s)", item.product().name(), item.product().market())
                    .isEqualTo("NL");
            assertThat(item.product().currency()).isEqualTo("EUR");
        }
    }

    // ---------- end-to-end through the real prompt → parse → plan path ----------

    @Test
    void parserPath_explicitCroatianToiletRequestInNl_isHonestlyDegraded() {
        // The rule-based extractor parses "WC školjka" → toilet + room bathroom; the NL market then can't supply it.
        PlannerInputDto raw = new PlannerInputDto("treba mi WC školjka", 0, "living-room", "modern", "", 8, "multi",
                List.of(), "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), "NL");
        PlanGenerationResponse r = planner.generate(raw);

        assertThat(r.input().roomType()).isEqualTo("bathroom");
        assertThat(r.input().mustHaveCategories()).contains("toilet");
        assertThat(r.partialPlan()).isTrue();
        assertThat(r.missingImportantCategories()).contains("toilet");
        assertOnlyFromMarket(r, "NL");
    }

    // ---------- helpers ----------

    private void assertFixtureDelivered(String market, List<String> mustHave, String category) {
        PlanGenerationResponse r = plan(market, 6000, mustHave, List.of());
        assertThat(r.missingImportantCategories())
                .as("%s has real %s stock → not flagged unavailable", market, category)
                .doesNotContain(category);
        PlanItemDto item = allItems(r).stream()
                .filter(i -> category.equalsIgnoreCase(i.product().category()))
                .findFirst().orElse(null);
        assertThat(item).as("a real %s %s is delivered", market, category).isNotNull();
        assertThat(item.product().market()).isEqualTo(market);
        assertThat(item.product().currency()).isEqualTo(Markets.currencyFor(market));
        assertThat(item.product().productUrl()).isNotBlank();
        assertThat(item.product().price()).isNotNull();
        assertThat(item.product().price().signum()).isPositive();
    }

    private PlanGenerationResponse plan(String market, int budget, List<String> mustHave, List<String> alreadyHave) {
        PlannerInputDto input = new PlannerInputDto("", budget, "bathroom", "modern", "", 8, "multi", List.of(),
                "best-value", "comfort", mustHave, alreadyHave, List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), market);
        return planner.generateResolved(input);
    }

    private static List<PlanItemDto> primaryItems(PlanGenerationResponse r) {
        return r.plans().isEmpty() ? List.of() : r.plans().get(0).items();
    }

    private static List<PlanItemDto> allItems(PlanGenerationResponse r) {
        List<PlanItemDto> items = new ArrayList<>();
        for (FurnishingPlanDto plan : r.plans()) items.addAll(plan.items());
        return items;
    }

    private static List<String> categoriesInAnyTier(PlanGenerationResponse r) {
        return allItems(r).stream().map(i -> i.product().category()).toList();
    }

    private static PlanItemDto fixtureItem(PlanGenerationResponse r) {
        return allItems(r).stream()
                .filter(i -> ProductTaxonomy.isFixtureCategory(i.product().category()))
                .findFirst().orElse(null);
    }

    private static void assertOnlyFromMarket(PlanGenerationResponse r, String market) {
        String currency = Markets.currencyFor(market);
        for (PlanItemDto item : allItems(r)) {
            assertThat(item.product().market())
                    .as("no foreign-market product: %s (%s)", item.product().name(), item.product().market())
                    .isEqualTo(market);
            assertThat(item.product().currency())
                    .as("no wrong currency: %s", item.product().name())
                    .isEqualTo(currency);
            assertThat(item.product().productUrl()).as("url present: %s", item.product().name()).isNotBlank();
            assertThat(item.product().imageUrl()).as("image present: %s", item.product().name()).isNotBlank();
            assertThat(item.product().price()).as("price present: %s", item.product().name()).isNotNull();
            assertThat(item.product().price().signum()).isPositive();
        }
    }

    private static PlannerService plannerFromCatalog(List<Product> all) {
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
            try (InputStream in = DegradedCapacityBathroomTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        ImportSummaryDto summary = importer.importSnapshot(all);
        assertThat(summary.errors()).as("import errors").isEmpty();
        return saved;
    }
}
