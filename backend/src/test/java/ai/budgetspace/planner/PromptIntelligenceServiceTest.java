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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptIntelligenceServiceTest {

    @Test
    void aiDisabledUsesRuleBasedFallback() {
        PromptIntelligenceService service = service(disabled(), defaultTracker(), fixedClient("{}"));

        PlannerIntentAnalysisDto analysis = service.analyze(input("Imam 1500 € za dnevni boravak, treba mi kauč."), "s1");

        assertThat(analysis.aiUsed()).isFalse();
        assertThat(analysis.source()).isEqualTo("rule-based");
        assertThat(analysis.roomType()).isEqualTo("living-room");
        assertThat(analysis.mustHaveCategories()).contains("sofa");
    }

    @Test
    void missingApiKeyUsesFallback() {
        PromptIntelligenceService service = service(enabledOpenAi(""), defaultTracker(), throwingClient());

        PlannerIntentAnalysisDto analysis = service.analyze(input("Spavaća soba do 1200 €, krevet i madrac."), "s1");

        assertThat(analysis.aiUsed()).isFalse();
        assertThat(analysis.roomType()).isEqualTo("bedroom");
    }

    @Test
    void malformedLlmResponseFallsBack() {
        PromptIntelligenceService service = service(enabledOpenAi("key"), defaultTracker(), fixedClient("ovo nije json"));

        PlannerIntentAnalysisDto analysis = service.analyze(input("Dnevni boravak, treba mi kauč."), "s1");

        assertThat(analysis.aiUsed()).isFalse();
        assertThat(analysis.mustHaveCategories()).contains("sofa");
    }

    @Test
    void llmFailureFallsBack() {
        PromptIntelligenceService service = service(enabledOpenAi("key"), defaultTracker(), throwingClient());

        PlannerIntentAnalysisDto analysis = service.analyze(input("Radni kutak, treba mi stolica."), "s1");

        assertThat(analysis.aiUsed()).isFalse();
        assertThat(analysis.roomType()).isEqualTo("home-office");
    }

    @Test
    void usageLimitExceededFallsBackWithoutCallingAi() {
        AiUsageTracker blocked = new AiUsageTracker(20, 0, 10, 0.0002, 0.0008); // 0 requests/day allowed
        PromptIntelligenceService service = service(enabledOpenAi("key"), blocked, throwingClient());

        PlannerIntentAnalysisDto analysis = service.analyze(input("Dnevni boravak, treba mi kauč."), "s1");

        assertThat(analysis.aiUsed()).isFalse(); // limit blocked AI; throwingClient was never called
        assertThat(analysis.mustHaveCategories()).contains("sofa");
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

        PlannerIntentAnalysisDto analysis = service.analyze(input("opremi mi kuhinju"), "s1");

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
                input("Imam 2000 € za kuhinju, želim drvo i bijelo, treba mi kuhinjska kolica."), "s1");

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
        return new LlmProperties(true, "openai", key, "", "", 15, 700);
    }

    private LlmProperties disabled() {
        return new LlmProperties(false, "off", "", "", "", 15, 700);
    }

    private AiUsageTracker defaultTracker() {
        return new AiUsageTracker(20, 100, 10, 0.0002, 0.0008);
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
