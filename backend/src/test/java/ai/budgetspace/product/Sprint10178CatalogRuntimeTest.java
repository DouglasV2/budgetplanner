package ai.budgetspace.product;

import ai.budgetspace.dto.CompleteKitchenDto;
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
import ai.budgetspace.planner.KitchenIntentClassifier;
import ai.budgetspace.planner.PlannerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.178 — targeted catalog expansion (per the owner's brief): IKEA wash-basin/vanity fixtures + bathroom
 * mirrors for every market, complete kitchen SETS + appliances for the 12 markets that had none, and GB core-furniture
 * depth. This test proves the new resources actually seed, dedupe cleanly (no import errors), belong to the right
 * market/room/category, and are <b>planner-eligible</b> (so they truly enter a plan — a JSON row the planner can't use
 * does not count). It also locks in the market-leak fix (no eligible catalog row may have a blank/unknown market).
 */
class Sprint10178CatalogRuntimeTest {

    private static final List<String> MARKETS_15 = List.of(
            "HR", "SI", "AT", "DE", "DK", "SE", "NO", "FI", "NL", "FR", "IT", "ES", "PT", "SK", "GB");
    private static final List<String> KITCHEN_SET_MARKETS = List.of(
            "SI", "DK", "SE", "NO", "FI", "NL", "FR", "IT", "ES", "PT", "SK", "GB");
    private static final Set<String> APPLIANCE_CATEGORIES = Set.of(
            "oven", "hob", "cooker-hood", "fridge", "freezer", "dishwasher", "microwave");

    @Test
    void washbasinFixturesCoverEveryMarketAndArePlannerEligible() throws Exception {
        List<Product> saved = importResource("/catalog/real-ikea-bathroom-washbasin-10-178.json");
        assertBathroomCatalog(saved, "washbasin");
        // Every one of the 15 markets now has a real wash-basin fixture (was 0 in 13 markets before this sprint).
        Map<String, Long> perMarket = countByMarket(saved);
        for (String market : MARKETS_15) {
            assertThat(perMarket.getOrDefault(market, 0L))
                    .as("washbasin fixtures for %s", market).isGreaterThanOrEqualTo(6L);
        }
    }

    @Test
    void bathroomMirrorsCoverEveryMarket() throws Exception {
        List<Product> saved = importResource("/catalog/real-ikea-bathroom-mirror-10-178.json");
        assertBathroomCatalog(saved, "decor");
        Map<String, Long> perMarket = countByMarket(saved);
        for (String market : MARKETS_15) {
            assertThat(perMarket.getOrDefault(market, 0L))
                    .as("bathroom mirrors for %s", market).isGreaterThanOrEqualTo(4L);
        }
    }

    @Test
    void kitchenSetsCoverTheTwelveExpansionMarketsAndArePlannerEligible() throws Exception {
        List<Product> saved = importResource("/catalog/real-ikea-kitchen-sets-eu-10-178.json");
        assertThat(saved).isNotEmpty();
        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getCategory()).as("kitchen-set %s", product.getExternalId()).isEqualTo("kitchen-set");
            assertThat(product.getRoomTags()).as("kitchen tag %s", product.getExternalId()).contains("kitchen");
            assertThat(product.getPrice().signum()).isPositive();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });
        Map<String, Long> perMarket = countByMarket(saved);
        for (String market : KITCHEN_SET_MARKETS) {
            assertThat(perMarket.getOrDefault(market, 0L))
                    .as("kitchen sets for %s", market).isGreaterThanOrEqualTo(6L);
        }
    }

    @Test
    void kitchenAppliancesImportAsEligibleKitchenProducts() throws Exception {
        List<Product> saved = importResource("/catalog/real-ikea-kitchen-appliances-eu-10-178.json");
        assertThat(saved).isNotEmpty();
        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getCategory()).as("appliance category %s", product.getExternalId()).isIn(APPLIANCE_CATEGORIES);
            assertThat(product.getRoomTags()).as("kitchen tag %s", product.getExternalId()).contains("kitchen");
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });
        // An oven is the anchor appliance; it must exist in a broad set of markets (GB is honestly thin — IKEA
        // barely stocks appliances in the UK — so we require >=10 of the 12, not all 12).
        long marketsWithOven = saved.stream()
                .filter(p -> "oven".equals(p.getCategory()))
                .map(Product::getMarket).collect(Collectors.toCollection(LinkedHashSet::new)).size();
        assertThat(marketsWithOven).as("markets with an IKEA oven").isGreaterThanOrEqualTo(10L);
    }

    @Test
    void gbVictorianPlumbingBathroomFixturesImportAndArePlannerEligible() throws Exception {
        List<Product> saved = importResource("/catalog/real-vp-gb-bathroom-10-178.json");
        assertThat(saved).isNotEmpty();
        Set<String> fixtureCats = Set.of("toilet", "bath-shower");
        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("Victorian Plumbing");
            assertThat(product.getMarket()).isEqualTo("GB");
            assertThat(product.getCategory()).as("fixture %s", product.getExternalId()).isIn(fixtureCats);
            assertThat(product.getRoomTags()).as("bathroom tag %s", product.getExternalId()).contains("bathroom");
            assertThat(product.getProductUrl()).startsWith("https://www.victorianplumbing.co.uk/");
            // The whole point: Victorian Plumbing is MANUAL_VERIFIED_ONLY (not feed-required), so a
            // public-product-page row IS planner-eligible and can enter a GB bathroom plan (GB had no WC/bath before).
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });
        assertThat(saved).as("GB toilets").anySatisfy(p -> assertThat(p.getCategory()).isEqualTo("toilet"));
        assertThat(saved).as("GB baths/showers").anySatisfy(p -> assertThat(p.getCategory()).isEqualTo("bath-shower"));
    }

    @Test
    void gbCoreDepthRaisesTheThinCells() throws Exception {
        List<Product> gb = importWholeCatalog().stream()
                .filter(CatalogSourcePolicy::isPlannerEligible)
                .filter(p -> "GB".equalsIgnoreCase(p.getMarket()))
                .toList();
        // GB was the thinnest core market (sofa 10 / bed 11 / mattress 12, IKEA-only). This sprint raised each
        // above the "main category" floor the brief set (>=12) with headroom.
        assertThat(gb.stream().filter(byCategory("sofa")).count()).as("GB sofas").isGreaterThanOrEqualTo(18L);
        assertThat(gb.stream().filter(byCategory("bed")).count()).as("GB beds").isGreaterThanOrEqualTo(17L);
        assertThat(gb.stream().filter(byCategory("mattress")).count()).as("GB mattresses").isGreaterThanOrEqualTo(15L);
    }

    @Test
    void everyPlannerEligibleCatalogProductHasAKnownMarket() throws Exception {
        // Locks in the market-leak fix: legacy HR rows used to carry no market and were served (with HR EUR
        // prices/links) into every market. Now every planner-eligible product is scoped to a known market.
        List<Product> offenders = importWholeCatalog().stream()
                .filter(CatalogSourcePolicy::isPlannerEligible)
                .filter(p -> p.getMarket() == null || p.getMarket().isBlank() || !Markets.isKnown(p.getMarket()))
                .toList();
        assertThat(offenders)
                .as("planner-eligible products with a blank/unknown market: %s",
                        offenders.stream().map(Product::getExternalId).limit(20).toList())
                .isEmpty();
    }

    // ---- planner-level verification: the products actually surface in a generated plan ----

    @Test
    void deBathroomPlanActuallyIncludesAWashbasinFixture() throws Exception {
        PlannerService planner = plannerFromWholeCatalog();
        PlanGenerationResponse response = planner.generateResolved(bathroomInput("DE", 900));
        assertThat(planCategories(response))
                .as("a DE bathroom plan now offers a real wash-basin fixture (was impossible before this sprint)")
                .contains("washbasin");
    }

    @Test
    void gbBathroomPlanIncludesBothIkeaAndVictorianPlumbingFixtures() throws Exception {
        PlannerService planner = plannerFromWholeCatalog();
        List<String> categories = planCategories(planner.generateResolved(bathroomInput("GB", 1200)));
        // IKEA wash-basin AND a Victorian Plumbing WC / bath-shower both reach the plan.
        assertThat(categories).as("GB bathroom washbasin").contains("washbasin");
        assertThat(categories).as("GB bathroom WC or bath/shower (Victorian Plumbing)")
                .containsAnyOf("toilet", "bath-shower");
    }

    @Test
    void siCompleteKitchenPromptReturnsRealModularSets() throws Exception {
        PlannerService planner = plannerFromWholeCatalog();
        KitchenIntentClassifier.KitchenBrief brief = new KitchenIntentClassifier().classify("želim kompletnu kuhinju");
        assertThat(brief.intent()).isEqualTo(KitchenIntentClassifier.KitchenIntent.COMPLETE);
        CompleteKitchenDto complete = planner.buildCompleteKitchen(kitchenInput("SI", 3000), brief);
        assertThat(complete.sets()).as("SI complete-kitchen sets (was 0 before this sprint)").isNotEmpty();
        assertThat(complete.sets()).allSatisfy(set -> assertThat(set.category()).isEqualTo("kitchen-set"));
    }

    @Test
    void deBathroomPlanFillsAcrossBudgetTiers() throws Exception {
        PlannerService planner = plannerFromWholeCatalog();
        // Small / medium / large budgets all produce a non-empty bathroom plan that includes a wash-basin (scenarios
        // 3-5: mali/srednji/veći budžet). The cheapest DE wash-basin is ~€17, so even a tight budget fits one.
        for (int budget : new int[] {300, 900, 2000}) {
            PlanGenerationResponse response = planner.generateResolved(bathroomInput("DE", budget));
            assertThat(response.plans().get(0).items()).as("DE bathroom @%d€ non-empty", budget).isNotEmpty();
            assertThat(planCategories(response)).as("DE bathroom @%d€ has a washbasin", budget).contains("washbasin");
        }
    }

    @Test
    void findSimilarOnADeWashbasinReturnsDistinctOptions() throws Exception {
        List<Product> all = importWholeCatalog();
        Product anchor = all.stream()
                .filter(CatalogSourcePolicy::isPlannerEligible)
                .filter(p -> "DE".equals(p.getMarket()) && "washbasin".equals(p.getCategory()))
                .findFirst().orElseThrow();
        PlannerService planner = plannerFromCatalog(all);
        // "Find similar under budget" / "compare budget options" (scenarios 7-8): distinct alternatives to the anchor.
        SimilarItemsResponse res = planner.findSimilar(
                new SimilarItemsRequest(ProductDto.from(anchor), bathroomInput("DE", 900), 900));
        assertThat(res.bestValue()).as("a best-value washbasin alternative exists").isNotNull();
        assertThat(res.budgetPick()).as("a cheaper washbasin alternative exists").isNotNull();
        assertThat(res.bestValue().id()).isNotEqualTo(res.budgetPick().id());
        assertThat(res.bestValue().category()).isEqualTo("washbasin");
    }

    @Test
    void replacingADeWashbasinReturnsAnotherWashbasin() throws Exception {
        PlannerService planner = plannerFromWholeCatalog();
        FurnishingPlanDto plan = planner.generateResolved(bathroomInput("DE", 900)).plans().get(0);
        PlanItemDto washbasinItem = plan.items().stream()
                .filter(i -> "washbasin".equals(i.product().category())).findFirst().orElseThrow();
        FurnishingPlanDto replaced = planner.replaceProduct(
                new ReplaceProductRequest(plan, bathroomInput("DE", 900), washbasinItem.product().id(), "different"));
        // The swapped slot is still a washbasin, and a different product than the original (DE has 9 wash-basins).
        PlanItemDto swapped = replaced.items().stream()
                .filter(i -> "washbasin".equals(i.product().category())).findFirst().orElseThrow();
        assertThat(swapped.product().id())
                .as("replace returned a different washbasin").isNotEqualTo(washbasinItem.product().id());
    }

    private PlannerService plannerFromWholeCatalog() throws Exception {
        return plannerFromCatalog(importWholeCatalog());
    }

    private PlannerService plannerFromCatalog(List<Product> all) {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findAll()).thenReturn(all);
        return new PlannerService(repository);
    }

    private static PlannerInputDto bathroomInput(String market, int budget) {
        return marketRoomInput("kupaonica", budget, "bathroom", market);
    }

    private static PlannerInputDto kitchenInput(String market, int budget) {
        return marketRoomInput("kuhinja", budget, "kitchen", market);
    }

    private static PlannerInputDto marketRoomInput(String prompt, int budget, String room, String market) {
        return new PlannerInputDto(prompt, budget, room, "modern", "", 8, "multi", List.of(),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), market);
    }

    private static List<String> planCategories(PlanGenerationResponse response) {
        return response.plans().stream()
                .flatMap(plan -> plan.items().stream())
                .map(item -> item.product().category())
                .collect(Collectors.toList());
    }

    // ---- helpers ----

    private void assertBathroomCatalog(List<Product> saved, String category) {
        assertThat(saved).isNotEmpty();
        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getRetailer()).isEqualTo("IKEA");
            assertThat(product.getCategory()).as("category %s", product.getExternalId()).isEqualTo(category);
            assertThat(product.getRoomTags()).as("bathroom tag %s", product.getExternalId()).contains("bathroom");
            assertThat(product.getPrice().signum()).as("price>0 %s", product.getExternalId()).isPositive();
            assertThat(product.getProductUrl()).as("real /p/ url %s", product.getExternalId()).contains("ikea.com").contains("/p/");
            assertThat(product.isImageVerified()).as("imageVerified %s", product.getExternalId()).isTrue();
            assertThat(CatalogSourcePolicy.isPlannerEligible(product))
                    .as("planner-eligible %s", product.getExternalId()).isTrue();
        });
    }

    private static Predicate<Product> byCategory(String category) {
        return p -> category.equalsIgnoreCase(p.getCategory());
    }

    private static Map<String, Long> countByMarket(List<Product> products) {
        return products.stream().collect(Collectors.groupingBy(Product::getMarket, Collectors.counting()));
    }

    /** Import a single resource through the real pipeline; returns the deduped saved products. */
    private static List<Product> importResource(String resource) throws Exception {
        return importResources(List.of(resource));
    }

    /** Import every resource the seeder loads (dedupes by externalId, like production). */
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
            try (InputStream in = Sprint10178CatalogRuntimeTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        ImportSummaryDto summary = importer.importSnapshot(all);
        assertThat(summary.errors()).as("import errors for %s", resources).isEmpty();
        return saved;
    }
}
