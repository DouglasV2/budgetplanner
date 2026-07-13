package ai.budgetspace.planner;

import ai.budgetspace.ai.AiUsageTracker;
import ai.budgetspace.ai.LlmClient;
import ai.budgetspace.ai.LlmClientFactory;
import ai.budgetspace.ai.LlmCompletion;
import ai.budgetspace.ai.LlmCompletionRequest;
import ai.budgetspace.ai.LlmProperties;
import ai.budgetspace.ai.LlmProvider;
import ai.budgetspace.dto.FurnishingPlanDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.PlanGenerationResponse;
import ai.budgetspace.dto.PlanItemDto;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.PlannerIntentAnalysisDto;
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
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phases 12 & 13 — AI failure/fallback + planner API integration.
 *
 * <p>Everything here is DETERMINISTIC: the LLM is a fake client (no paid/live AI). Phase 12 drives every failure mode
 * the prompt-intelligence layer must survive (disabled, no key, usage cap, timeout, provider exception, malformed /
 * empty / incomplete JSON, scalar-for-array, unknown room/category/retailer/currency) and confirms the deterministic
 * rule-based fallback still yields a usable plan. Phase 13 runs representative prompts through the SAME decision the
 * controller makes (analyze → generate / generateResolved) and asserts the end-to-end plan invariants.</p>
 */
class AiFallbackAndPlannerApiTest {

    private static PlannerService planner;

    @BeforeAll
    static void importCatalogOnce() throws Exception {
        planner = plannerFromCatalog(importWholeCatalog());
    }

    // ============================ Phase 12 — AI failure / fallback ============================

    @Test
    void aiDisabled_fallsBackToRuleBased_andStillPlans() {
        PlanGenerationResponse r = runLikeController(service(disabled(), defaultTracker()),
                input("Dnevni boravak, treba mi kauč, 2000 eura", "HR"));
        assertThat(r.intentAnalysis().aiUsed()).isFalse();
        assertThat(r.input().roomType()).isEqualTo("living-room");
        assertThat(categories(r)).contains("sofa");
        assertThat(primary(r).items()).isNotEmpty();
    }

    @Test
    void missingApiKey_fallsBack() {
        PlanGenerationResponse r = runLikeController(service(enabledOpenAi(""), defaultTracker(), throwing(new RuntimeException("no key"))),
                input("Spavaća soba, krevet i madrac, 1500 eura", "HR"));
        assertThat(r.intentAnalysis().aiUsed()).isFalse();
        assertThat(r.input().roomType()).isEqualTo("bedroom");
        assertThat(primary(r).items()).isNotEmpty();
    }

    @Test
    void usageLimitReached_fallsBackWithoutCallingAi() {
        AiUsageTracker blocked = new AiUsageTracker(20, 0, 3, 10, 100, 500, 0.0002, 0.0008); // 0 requests/day
        PlanGenerationResponse r = runLikeController(service(enabledOpenAi("key"), blocked, throwing(new RuntimeException("must not be called"))),
                input("Dnevni boravak, treba mi kauč", "HR"), "guest:s1", "FREE");
        assertThat(r.intentAnalysis().aiUsed()).isFalse();
        assertThat(primary(r).items()).isNotEmpty();
    }

    @Test
    void timeout_fallsBack() {
        PlanGenerationResponse r = runLikeController(service(enabledOpenAi("key"), defaultTracker(), throwing(new SocketTimeoutException("Read timed out"))),
                input("Radni kutak, treba mi stol i stolica, 900 eura", "HR"));
        assertThat(r.intentAnalysis().aiUsed()).isFalse();
        assertThat(r.input().roomType()).isEqualTo("home-office");
        assertThat(primary(r).items()).isNotEmpty();
    }

    @Test
    void providerException_fallsBack() {
        PlanGenerationResponse r = runLikeController(service(enabledOpenAi("key"), defaultTracker(), throwing(new RuntimeException("provider 500"))),
                input("Kuhinja, treba mi frižider, 1200 eura", "HR"));
        assertThat(r.intentAnalysis().aiUsed()).isFalse();
        assertThat(r.input().roomType()).isEqualTo("kitchen");
    }

    @Test
    void malformedJson_fallsBack() {
        PlanGenerationResponse r = runLikeController(service(enabledOpenAi("key"), defaultTracker(), fixed("ovo nije json")),
                input("Dnevni boravak, treba mi kauč", "HR"));
        assertThat(r.intentAnalysis().aiUsed()).isFalse();
        assertThat(categories(r)).contains("sofa");
    }

    @Test
    void emptyResponse_fallsBack() {
        // A truly empty completion has no JSON to parse → fallback.
        PlanGenerationResponse r = runLikeController(service(enabledOpenAi("key"), defaultTracker(), fixed("")),
                input("Dnevni boravak, treba mi kauč", "HR"));
        assertThat(r.intentAnalysis().aiUsed()).isFalse();
        assertThat(primary(r).items()).isNotEmpty();
    }

    @Test
    void emptyJsonObject_isHandledGracefully_notACrash() {
        // "{}" parses to an all-null analysis; the market/room defaults keep the plan usable (AI "used" but empty).
        PlanGenerationResponse r = runLikeController(service(enabledOpenAi("key"), defaultTracker(), fixed("{}")),
                input("Dnevni boravak, treba mi kauč", "HR"));
        assertThat(r.plans()).hasSize(3);
        assertThat(primary(r).items()).isNotEmpty();
    }

    @Test
    void incompleteJson_fallsBack() {
        PlanGenerationResponse r = runLikeController(service(enabledOpenAi("key"), defaultTracker(), fixed("{\"roomType\":\"living-room\",\"budget\":")),
                input("Dnevni boravak, treba mi kauč", "HR"));
        assertThat(r.intentAnalysis().aiUsed()).isFalse();
        assertThat(categories(r)).contains("sofa");
    }

    @Test
    void scalarForArrayField_isAcceptedNotDroppedToFallback() {
        String json = "{\"roomType\":\"living-room\",\"budget\":1000,\"colorPreferences\":\"warm\"}";
        PlannerIntentAnalysisDto a = service(enabledOpenAi("key"), defaultTracker(), fixed(json)).analyze(input("warm living room", "HR"), "s1", "FREE");
        assertThat(a.aiUsed()).as("a scalar where a list is expected must not force a fallback").isTrue();
        assertThat(a.colorPreferences()).contains("warm");
    }

    @Test
    void unknownRoomCategoryRetailerCurrency_areSanitizedAndNeverPolluteThePlan() {
        String json = "{\"roomType\":\"dungeon\",\"budget\":2000,\"currency\":\"XYZ\","
                + "\"mustHaveCategories\":[\"sofa\",\"flux-capacitor\"],"
                + "\"preferredRetailers\":[\"IKEA\",\"TotallyFakeStore\"]}";
        PromptIntelligenceService svc = service(enabledOpenAi("key"), defaultTracker(), fixed(json));
        PlannerIntentAnalysisDto a = svc.analyze(input("dnevni boravak, kauč", "HR"), "s1", "FREE");

        assertThat(a.aiUsed()).isTrue();
        assertThat(a.roomType()).as("unknown room dropped → not 'dungeon'").isNotEqualTo("dungeon");
        assertThat(a.currency()).as("currency is the market's, never the model's bogus code").isEqualTo("EUR");
        assertThat(a.mustHaveCategories()).contains("sofa").doesNotContain("flux-capacitor");
        assertThat(a.preferredRetailers()).contains("IKEA").doesNotContain("TotallyFakeStore");
    }

    /**
     * The brief's crucial fallback case: a slangy, telegraphic Croatian prompt must be understood by the rule-based
     * layer even when AI is unavailable — living-room, 1000 EUR (soma), a full-room plan of ONLY the market's
     * products, and never a silent default to 1500.
     */
    @Test
    void ruleBasedFallback_understandsBoravakZaSomaEura() {
        PlanGenerationResponse r = runLikeController(service(disabled(), defaultTracker()),
                input("e boravak za soma eura", "HR"));

        assertThat(r.intentAnalysis().aiUsed()).isFalse();
        assertThat(r.input().roomType()).as("boravak → living-room").isEqualTo("living-room");
        assertThat(r.input().budget()).as("soma → 1000, NOT the silent 1500 default").isEqualTo(1000);
        assertThat(primary(r).items()).as("a full-room, non-empty plan").isNotEmpty();
        // Only the selected market's products.
        for (PlanItemDto item : allItems(r)) {
            assertThat(item.product().market()).isEqualTo("HR");
            assertThat(item.product().currency()).isEqualTo("EUR");
        }
        // The value tier respects the parsed 1000 budget.
        assertThat(primary(r).total().doubleValue()).isLessThanOrEqualTo(1000);
    }

    // ============================ Phase 13 — planner API integration ============================

    @Test
    void integration_hrLivingRoom_mustHaveAndAlreadyOwned() {
        PlanGenerationResponse r = runLikeController(service(disabled(), defaultTracker()),
                input("Dnevni boravak, 2500 eura, tepih imam, treba mi kauč i TV komoda", "HR"));
        assertThat(r.input().roomType()).isEqualTo("living-room");
        assertThat(r.input().budget()).isEqualTo(2500);
        assertThat(r.input().mustHaveCategories()).contains("sofa", "tv-unit");
        assertThat(r.input().alreadyHaveCategories()).contains("rug");
        assertThat(categories(r)).contains("sofa", "tv-unit").doesNotContain("rug"); // owned rug not re-bought
        assertHealthyPlan(r, "HR", 2500);
    }

    @Test
    void integration_deBedroom_currencyAndMarketCorrect() {
        PlanGenerationResponse r = runLikeController(service(disabled(), defaultTracker()),
                input("Schlafzimmer, 1500 Euro, Bett und Matratze", "DE"));
        assertThat(r.input().roomType()).isEqualTo("bedroom");
        assertThat(categories(r)).contains("bed", "mattress");
        assertHealthyPlan(r, "DE", 1500);
    }

    @Test
    void integration_norwayLivingRoom_nonEurCurrency() {
        PlanGenerationResponse r = runLikeController(service(disabled(), defaultTracker()),
                input("stue, 15000 kr, sofa", "NO"));
        assertThat(r.input().roomType()).isEqualTo("living-room");
        assertThat(r.input().budget()).isEqualTo(15000);
        assertThat(Markets.currencyFor(r.input().market())).isEqualTo("NOK");
        assertHealthyPlan(r, "NO", 15000);
    }

    @Test
    void integration_bathroomSubtype_showerNotBathtub() {
        PlanGenerationResponse r = runLikeController(service(disabled(), defaultTracker()),
                input("kupaonica 3000 eura, treba mi tuš, kadu neću", "HR"));
        assertThat(r.input().roomType()).isEqualTo("bathroom");
        assertThat(r.input().mustHaveCategories()).contains("shower");
        assertThat(r.input().alreadyHaveCategories()).contains("bathtub");
        PlanItemDto fixture = allItems(r).stream()
                .filter(i -> ProductTaxonomy.isFixtureCategory(i.product().category()))
                .findFirst().orElse(null);
        assertThat(fixture).as("a wet fixture is in the plan").isNotNull();
        assertThat(ProductTaxonomy.isShowerFixture(fixture.product().category(), fixture.product().name()))
                .as("explicit shower request → a shower: %s", fixture.product().name()).isTrue();
        assertThat(ProductTaxonomy.isBathtubFixture(fixture.product().category(), fixture.product().name()))
                .as("...and the excluded bathtub never appears").isFalse();
        assertHealthyPlan(r, "HR", 3000);
    }

    @Test
    void integration_retailerExclusionIsRespected() {
        PlanGenerationResponse r = runLikeController(service(disabled(), defaultTracker()),
                input("dnevni boravak 2500 eura, bez IKEA-e", "HR"));
        for (PlanItemDto item : allItems(r)) {
            assertThat(item.product().retailer()).as("IKEA excluded").isNotEqualTo("IKEA");
        }
        assertHealthyPlan(r, "HR", 2500);
    }

    @Test
    void integration_aiEnabledPath_validJsonIsUsedThenPlanned() {
        // Exercises the OTHER controller branch: aiUsed → generateResolved(toPlannerInput(...)).
        String json = "{\"roomType\":\"home-office\",\"budget\":1400,\"style\":\"modern\","
                + "\"mustHaveCategories\":[\"desk\",\"chair\"],\"qualityPreference\":\"balanced\"}";
        PlanGenerationResponse r = runLikeController(service(enabledOpenAi("key"), defaultTracker(), fixed(json)),
                input("set up my home office", "HR"));
        assertThat(r.intentAnalysis().aiUsed()).isTrue();
        assertThat(r.input().roomType()).isEqualTo("home-office");
        assertThat(categories(r)).contains("desk", "chair");
        assertHealthyPlan(r, "HR", 1400);
    }

    // ---------- controller-faithful runner + invariants ----------

    // Mirrors PlanController.generate: understand the prompt (AI when usable, else rule-based), then plan on the
    // matching path. maybeAttachCompleteKitchen is orthogonal to these assertions and omitted.
    private PlanGenerationResponse runLikeController(PromptIntelligenceService svc, PlannerInputDto input) {
        return runLikeController(svc, input, "s1", "FREE");
    }

    private PlanGenerationResponse runLikeController(PromptIntelligenceService svc, PlannerInputDto input, String owner, String tier) {
        PlannerIntentAnalysisDto analysis = svc.analyze(input, owner, tier);
        PlannerInputDto resolved = analysis.aiUsed() ? svc.toPlannerInput(analysis, input) : input;
        PlanGenerationResponse response = analysis.aiUsed()
                ? planner.generateResolved(resolved, analysis.specificItemsOnly())
                : planner.generate(input);
        return response.withAnalysis(analysis);
    }

    private void assertHealthyPlan(PlanGenerationResponse r, String market, int budget) {
        assertThat(r.plans()).as("three tiers").hasSize(3);
        String currency = Markets.currencyFor(market);
        for (FurnishingPlanDto plan : r.plans()) {
            assertThat(plan.items()).as("tier %s is non-empty", plan.id()).isNotEmpty();
            assertThat(plan.total()).isNotNull();
            assertThat(plan.total().signum()).as("tier %s total positive", plan.id()).isPositive();
            Set<String> ids = new HashSet<>();
            for (PlanItemDto item : plan.items()) {
                assertThat(ids.add(item.product().id()))
                        .as("no duplicate product %s in tier %s", item.product().id(), plan.id()).isTrue();
                assertThat(item.product().market()).as("only the selected market: %s", item.product().name()).isEqualTo(market);
                assertThat(item.product().currency()).as("currency for %s", item.product().name()).isEqualTo(currency);
                assertThat(item.product().productUrl()).as("url for %s", item.product().name()).isNotBlank();
                assertThat(item.product().imageUrl()).as("image for %s", item.product().name()).isNotBlank();
                assertThat(item.product().price()).as("price for %s", item.product().name()).isNotNull();
                assertThat(item.product().price().signum()).isPositive();
            }
        }
        assertThat(primary(r).total().doubleValue()).as("value tier within budget").isLessThanOrEqualTo(budget);
    }

    private static FurnishingPlanDto primary(PlanGenerationResponse r) {
        return r.plans().get(0);
    }

    private static List<PlanItemDto> allItems(PlanGenerationResponse r) {
        List<PlanItemDto> items = new ArrayList<>();
        for (FurnishingPlanDto plan : r.plans()) items.addAll(plan.items());
        return items;
    }

    private static List<String> categories(PlanGenerationResponse r) {
        return primary(r).items().stream().map(i -> i.product().category()).toList();
    }

    // ---------- fake LLM plumbing (no live AI) ----------

    private PromptIntelligenceService service(LlmProperties props, AiUsageTracker tracker, LlmClient... clients) {
        return new PromptIntelligenceService(new LlmClientFactory(props, List.of(clients)), props, tracker);
    }

    private LlmProperties enabledOpenAi(String key) {
        return new LlmProperties(true, "openai", key, "", "", "", 15, 700);
    }

    private LlmProperties disabled() {
        return new LlmProperties(false, "off", "", "", "", "", 15, 700);
    }

    private AiUsageTracker defaultTracker() {
        return new AiUsageTracker(20, 2000, 3, 10, 100, 500, 0.0002, 0.0008);
    }

    private LlmClient fixed(String response) {
        return new LlmClient() {
            public LlmProvider provider() { return LlmProvider.OPENAI; }
            public LlmCompletion complete(LlmCompletionRequest request) {
                return new LlmCompletion(response, 100, 50, "fake-model");
            }
        };
    }

    private LlmClient throwing(Exception exception) {
        return new LlmClient() {
            public LlmProvider provider() { return LlmProvider.OPENAI; }
            public LlmCompletion complete(LlmCompletionRequest request) throws Exception { throw exception; }
        };
    }

    private PlannerInputDto input(String prompt, String market) {
        return new PlannerInputDto(prompt, 0, "living-room", "bright", "Zagreb", 20, "multi",
                List.of(), "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), market);
    }

    // ---------- whole-catalog import ----------

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
            try (InputStream in = AiFallbackAndPlannerApiTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("catalog resource %s", resource).isNotNull();
                all.addAll(mapper.readValue(in, new TypeReference<List<RetailerProductSnapshotDto>>() {}));
            }
        }
        ImportSummaryDto summary = importer.importSnapshot(all);
        assertThat(summary.errors()).as("import errors").isEmpty();
        return saved;
    }
}
