package ai.budgetspace.planner;

import ai.budgetspace.dto.PlannerInputDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.181 (Phase 4) — the version-controlled, data-driven real-user prompt matrix. Every case is a hand-authored
 * {prompt, expected intent} pair in {@code /prompts/prompt-matrix.json}; this harness runs each prompt through the REAL
 * deterministic parser ({@link PlannerIntentExtractor}, which is also the AI-path fallback) and asserts only the fields
 * a case declares. Expected values are deterministic and reviewable — never produced by an LLM.
 *
 * <p>Covers formal / conversational / telegraphic / typo / slang / mixed-language prompts across the 15 active markets,
 * plus adversarial and false-positive guards (Phase 10).</p>
 */
class PlannerRealUserPromptMatrixTest {

    private static final PlannerIntentExtractor EX = new PlannerIntentExtractor();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromptCase(
            String id, String market, String lang, String register, String prompt,
            String room, Integer budget, Boolean noBudget,
            List<String> mustHave, List<String> alreadyHave, List<String> excludedRetailers,
            List<String> preferredRetailers, Integer maxStores, String style,
            // Sprint 10.190: the matrix could not assert these two before, which is exactly why the negation
            // gaps ("not cheap" -> lowest-price, "not too basic" -> basic) never showed up here.
            String furnishingLevel, String optimizationGoal) {
    }

    static List<PromptCase> cases() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = PlannerRealUserPromptMatrixTest.class.getResourceAsStream("/prompts/prompt-matrix.json")) {
            assertThat(in).as("prompt-matrix.json on the test classpath").isNotNull();
            return mapper.readValue(in, new TypeReference<List<PromptCase>>() {});
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void matrixCase(PromptCase c) {
        PlannerInputDto enriched = EX.enrich(input(c.prompt(), c.market()));

        if (c.room() != null) {
            assertThat(enriched.roomType()).as("%s room", c.id()).isEqualTo(c.room());
        }
        if (c.budget() != null) {
            assertThat(enriched.budget()).as("%s budget", c.id()).isEqualTo(c.budget());
        }
        if (Boolean.TRUE.equals(c.noBudget())) {
            // input carries a sentinel budget (4321) no prompt parses to; if it survives, nothing was extracted.
            assertThat(enriched.budget()).as("%s must NOT parse a budget", c.id()).isEqualTo(4321);
        }
        if (c.mustHave() != null) {
            assertThat(enriched.mustHaveCategories()).as("%s mustHave", c.id()).containsAll(c.mustHave());
        }
        if (c.alreadyHave() != null) {
            assertThat(enriched.alreadyHaveCategories()).as("%s alreadyHave", c.id()).containsAll(c.alreadyHave());
        }
        if (c.excludedRetailers() != null) {
            assertThat(enriched.excludedRetailers()).as("%s excludedRetailers", c.id()).containsAll(c.excludedRetailers());
        }
        if (c.preferredRetailers() != null) {
            assertThat(enriched.preferredRetailers()).as("%s preferredRetailers", c.id()).containsAll(c.preferredRetailers());
        }
        if (c.maxStores() != null) {
            assertThat(enriched.maxStores()).as("%s maxStores", c.id()).isEqualTo(c.maxStores());
        }
        if (c.style() != null) {
            assertThat(enriched.style()).as("%s style", c.id()).isEqualTo(c.style());
        }
        if (c.furnishingLevel() != null) {
            assertThat(enriched.furnishingLevel()).as("%s furnishingLevel", c.id()).isEqualTo(c.furnishingLevel());
        }
        if (c.optimizationGoal() != null) {
            assertThat(enriched.optimizationGoal()).as("%s optimizationGoal", c.id()).isEqualTo(c.optimizationGoal());
        }
    }

    @Test
    void matrixHasStrongCoverage() throws Exception {
        List<PromptCase> cases = cases();
        assertThat(cases.size()).as("total prompt cases").isGreaterThanOrEqualTo(300);
        Map<String, Long> byMarket = cases.stream()
                .filter(c -> c.market() != null)
                .collect(Collectors.groupingBy(PromptCase::market, Collectors.counting()));
        for (String market : List.of("HR", "SI", "AT", "DE", "IT", "FI", "FR", "NL", "SK", "ES", "PT", "NO", "SE", "DK", "GB")) {
            assertThat(byMarket.getOrDefault(market, 0L)).as("cases for %s", market).isGreaterThanOrEqualTo(12L);
        }
        long adversarial = cases.stream().filter(c -> "adversarial".equals(c.register()) || "state".equals(c.register())).count();
        assertThat(adversarial).as("adversarial + state-transition cases").isGreaterThanOrEqualTo(40L);
        // Sprint 10.190: keep a real negation register in the matrix, so a future edit that re-breaks negation
        // fails here and not in front of a user.
        long negation = cases.stream().filter(c -> "negation".equals(c.register())).count();
        assertThat(negation).as("negation cases").isGreaterThanOrEqualTo(20L);
        // unique ids
        assertThat(cases.stream().map(PromptCase::id).distinct().count()).as("unique ids").isEqualTo(cases.size());
    }

    private static PlannerInputDto input(String prompt, String market) {
        return new PlannerInputDto(prompt, 4321, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), market == null ? "HR" : market);
    }
}
