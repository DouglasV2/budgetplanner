package ai.budgetspace.planner;

import ai.budgetspace.ai.AiUsageTracker;
import ai.budgetspace.ai.LlmClient;
import ai.budgetspace.ai.LlmClientFactory;
import ai.budgetspace.ai.LlmCompletion;
import ai.budgetspace.ai.LlmCompletionRequest;
import ai.budgetspace.ai.LlmProperties;
import ai.budgetspace.ai.LlmProvider;
import ai.budgetspace.dto.PlannerInputDto;
import ai.budgetspace.dto.PlannerIntentAnalysisDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PromptIntelligenceServiceTest {

    @Test
    void aiDisabledUsesRuleBasedFallback() {
        PromptIntelligenceService service = service(disabled(), defaultTracker(), fixedClient("{}"));

        PlannerIntentAnalysisDto analysis = service.analyze(input("Imam 1500 € za dnevni boravak, treba mi kauč."), "s1", "FREE");

        assertThat(analysis.aiUsed()).isFalse();
        assertThat(analysis.source()).isEqualTo("rule-based");
        assertThat(analysis.roomType()).isEqualTo("living-room");
        assertThat(analysis.mustHaveCategories()).contains("sofa");
    }

    @Test
    void missingApiKeyUsesFallback() {
        PromptIntelligenceService service = service(enabledOpenAi(""), defaultTracker(), throwingClient());

        PlannerIntentAnalysisDto analysis = service.analyze(input("Spavaća soba do 1200 €, krevet i madrac."), "s1", "FREE");

        assertThat(analysis.aiUsed()).isFalse();
        assertThat(analysis.roomType()).isEqualTo("bedroom");
    }

    @Test
    void malformedLlmResponseFallsBack() {
        PromptIntelligenceService service = service(enabledOpenAi("key"), defaultTracker(), fixedClient("ovo nije json"));

        PlannerIntentAnalysisDto analysis = service.analyze(input("Dnevni boravak, treba mi kauč."), "s1", "FREE");

        assertThat(analysis.aiUsed()).isFalse();
        assertThat(analysis.mustHaveCategories()).contains("sofa");
    }

    @Test
    void llmFailureFallsBack() {
        PromptIntelligenceService service = service(enabledOpenAi("key"), defaultTracker(), throwingClient());

        PlannerIntentAnalysisDto analysis = service.analyze(input("Radni kutak, treba mi stolica."), "s1", "FREE");

        assertThat(analysis.aiUsed()).isFalse();
        assertThat(analysis.roomType()).isEqualTo("home-office");
    }

    @Test
    void usageLimitExceededFallsBackWithoutCallingAi() {
        AiUsageTracker blocked = new AiUsageTracker(20, 0, 3, 10, 100, 500, 0.0002, 0.0008); // 0 global requests/day
        PromptIntelligenceService service = service(enabledOpenAi("key"), blocked, throwingClient());

        PlannerIntentAnalysisDto analysis = service.analyze(input("Dnevni boravak, treba mi kauč."), "guest:s1", "FREE");

        assertThat(analysis.aiUsed()).isFalse(); // limit blocked AI; throwingClient was never called
        assertThat(analysis.mustHaveCategories()).contains("sofa");
    }

    @Test
    void perTierDailyCapFallsBackWhenOwnerExceedsAllowance() {
        // Guest allowance = 1/day → the 2nd call from the same owner falls back to rule-based.
        AiUsageTracker tracker = new AiUsageTracker(20, 2000, 1, 10, 100, 500, 0.0002, 0.0008);
        PromptIntelligenceService service = service(enabledOpenAi("key"), tracker,
                fixedClient("{\"roomType\":\"living-room\",\"budget\":1500}"));

        PlannerIntentAnalysisDto first = service.analyze(input("dnevni boravak"), "guest:b1", "GUEST");
        PlannerIntentAnalysisDto second = service.analyze(input("dnevni boravak"), "guest:b1", "GUEST");

        assertThat(first.aiUsed()).isTrue();   // within the guest allowance
        assertThat(second.aiUsed()).isFalse(); // allowance exhausted → rule-based
    }

    @Test
    void perTierCapIsPerOwnerNotGlobal() {
        // b1 exhausts its 1/day guest allowance; a different owner (Plus) is unaffected — caps are per-user.
        AiUsageTracker tracker = new AiUsageTracker(20, 2000, 1, 10, 100, 500, 0.0002, 0.0008);
        PromptIntelligenceService service = service(enabledOpenAi("key"), tracker,
                fixedClient("{\"roomType\":\"living-room\",\"budget\":1500}"));

        service.analyze(input("dnevni boravak"), "guest:b1", "GUEST");
        PlannerIntentAnalysisDto other = service.analyze(input("dnevni boravak"), "user:u2", "PLUS");

        assertThat(other.aiUsed()).isTrue();
    }

    @Test
    void perTierCapHoldsUnderConcurrentBurst() throws Exception {
        // Guest cap = 2. Fire 6 concurrent requests for ONE owner while the LLM call blocks, so admitted
        // reservations pile up. Without atomic reservation (TOCTOU) all 6 would read the same pre-record
        // count of 0 and slip through; with it, exactly the cap is admitted.
        AiUsageTracker tracker = new AiUsageTracker(20, 2000, 2, 10, 100, 500, 0.0002, 0.0008);
        CountDownLatch release = new CountDownLatch(1);
        LlmClient blockingClient = new LlmClient() {
            public LlmProvider provider() { return LlmProvider.OPENAI; }
            public LlmCompletion complete(LlmCompletionRequest request) throws Exception {
                release.await(5, TimeUnit.SECONDS); // hold the reservation in-flight
                return new LlmCompletion("{\"roomType\":\"living-room\",\"budget\":1500}", 100, 50, "fake");
            }
        };
        PromptIntelligenceService service = service(enabledOpenAi("key"), tracker, blockingClient);

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<PlannerIntentAnalysisDto>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> service.analyze(input("dnevni boravak"), "guest:b1", "GUEST")));
        }
        Thread.sleep(400);   // let the burst settle: the cap-many block in the LLM, the rest fall back
        release.countDown(); // unblock the admitted calls
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        long aiUsed = 0;
        for (Future<PlannerIntentAnalysisDto> f : futures) {
            if (f.get().aiUsed()) aiUsed++;
        }
        assertThat(aiUsed).isLessThanOrEqualTo(2); // the per-tier cap held despite the concurrent burst
    }

    @Test
    void validLlmResponseIsUsedSanitisedAndTracked() {
        AiUsageTracker tracker = defaultTracker();
        String json = """
                {"roomType":"kitchen","budget":2000,"style":"modern",
                 "mustHaveCategories":["dining-table","spaceship"],
                 "alreadyHaveCategories":["rug"],"preferredRetailers":["JYSK","TESCO"],
                 "qualityPreference":"premium","confidence":0.9,
                 "userGoalSummary":"Opremiti kuhinju","normalizedPrompt":"kuhinja 2000 eur"}""";
        PromptIntelligenceService service = service(enabledOpenAi("key"), tracker, fixedClient(json));

        PlannerIntentAnalysisDto analysis = service.analyze(input("opremi mi kuhinju"), "s1", "FREE");

        assertThat(analysis.aiUsed()).isTrue();
        assertThat(analysis.source()).isEqualTo("openai");
        assertThat(analysis.roomType()).isEqualTo("kitchen");
        assertThat(analysis.budget()).isEqualTo(2000);
        // Sanitised: unknown category and unsupported retailer dropped.
        assertThat(analysis.mustHaveCategories()).contains("dining-table").doesNotContain("spaceship");
        assertThat(analysis.preferredRetailers()).contains("JYSK").doesNotContain("TESCO");
        assertThat(tracker.monthlySpendUsd()).isGreaterThan(0.0);

        PlannerInputDto mapped = service.toPlannerInput(analysis, input("opremi mi kuhinju"));
        assertThat(mapped.roomType()).isEqualTo("kitchen");
        assertThat(mapped.mustHaveCategories()).doesNotContain("spaceship");
        assertThat(mapped.prompt()).isEmpty(); // cleared so the planner does not re-parse
    }

    @Test
    void ruleBasedParsesMessyCroatianPrompt() {
        PromptIntelligenceService service = service(disabled(), defaultTracker());

        PlannerIntentAnalysisDto analysis = service.analyze(
                input("Imam 2000 € za kuhinju, želim drvo i bijelo, treba mi kuhinjska kolica."), "s1", "FREE");

        assertThat(analysis.roomType()).isEqualTo("kitchen");
        assertThat(analysis.budget()).isEqualTo(2000);
        assertThat(analysis.colorPreferences()).contains("white");
        assertThat(analysis.materialPreferences()).contains("wood");
        assertThat(analysis.mustHaveCategories()).contains("kitchen-cart");
    }

    // --- helpers ---

    private PromptIntelligenceService service(LlmProperties props, AiUsageTracker tracker, LlmClient... clients) {
        return new PromptIntelligenceService(new LlmClientFactory(props, List.of(clients)), props, tracker);
    }

    private LlmProperties enabledOpenAi(String key) {
        // (enabled, provider, openAiKey, anthropicKey, geminiKey, model, timeoutSeconds, maxOutputTokens)
        return new LlmProperties(true, "openai", key, "", "", "", 15, 700);
    }

    private LlmProperties disabled() {
        return new LlmProperties(false, "off", "", "", "", "", 15, 700);
    }

    private AiUsageTracker defaultTracker() {
        // (monthlyUsd, globalDaily, guest, free, plus, pro, inCost, outCost)
        return new AiUsageTracker(20, 2000, 3, 10, 100, 500, 0.0002, 0.0008);
    }

    private PlannerInputDto input(String prompt) {
        return new PlannerInputDto(prompt, 1500, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK"), "best-value", "comfort",
                List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }

    private LlmClient fixedClient(String response) {
        return new LlmClient() {
            public LlmProvider provider() { return LlmProvider.OPENAI; }
            public LlmCompletion complete(LlmCompletionRequest request) {
                return new LlmCompletion(response, 100, 50, "fake-model");
            }
        };
    }

    private LlmClient throwingClient() {
        return new LlmClient() {
            public LlmProvider provider() { return LlmProvider.OPENAI; }
            public LlmCompletion complete(LlmCompletionRequest request) throws Exception {
                throw new RuntimeException("network down");
            }
        };
    }
}
