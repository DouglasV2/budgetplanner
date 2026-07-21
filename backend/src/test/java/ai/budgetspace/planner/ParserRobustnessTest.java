package ai.budgetspace.planner;

import ai.budgetspace.dto.PlannerInputDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Sprint 10.190: adversarial robustness. Real users type unpredictable things — emoji, mixed languages, stacked
 * negations, ALL CAPS, contradictions, keyboard mashing, code fragments, absurd numbers. None of it may crash a
 * parser or push a field out of bounds. This asserts the INVARIANTS that must hold for ANY input, over a large
 * curated + generated corpus; the exact classification of nonsense is not asserted (there is no right answer),
 * only that the result is always safe and usable.
 */
class ParserRobustnessTest {

    private final PlannerIntentExtractor extractor = new PlannerIntentExtractor();
    private final KitchenIntentClassifier kitchen = new KitchenIntentClassifier();

    // Every room the extractor is allowed to produce, plus the living-room seed it may leave untouched.
    private static final Set<String> ALLOWED_ROOMS = Set.of(
            "living-room", "home-office", "bedroom", "kitchen", "dining-room", "hallway",
            "bathroom", "garage", "pantry", "laundry", "attic", "basement", "studio");
    private static final Set<String> ALLOWED_STYLES = Set.of(
            "bright", "warm", "modern", "minimal", "classic", "industrial", "boho", "surprise", "scandinavian", "cozy");
    private static final Set<String> ALLOWED_LEVELS = Set.of("basic", "comfort", "complete");
    private static final Set<String> ALLOWED_GOALS = Set.of(
            "lowest-price", "lower-price", "best-value", "least-stores", "style-match");
    private static final List<String> MARKETS = List.of("HR", "DE", "GB", "NO", "IT", "ES", "FR", "FI", "SE");

    private static final List<String> CHAOS = List.of(
            "", "   ", "\n\n\t", "\0", "﻿", "😀🛋️🔥", "🏠 kuhinja 2000€ 🍳", "ĐŽŠĆČ ćčžšđ",
            "жзщчшгав", "日本語のプロンプト", "مرحبا مطبخ", "🇭🇷🇩🇪🇬🇧",
            "ne ne ne ne ne ne ne ne ne bez bez bez crne boje",
            "NEĆU JEFTINO ALI NEKA BUDE JEFTINO ALI NE PREVIŠE JEFTINO",
            "not not not not cheap", "no no no no no no ikea",
            "kauč sofa divano canapé sohva couch тросјед",
            "1000000000000000000000000 eur", "-500 eur", "0 eur", "0.00001 k", "1,2,3,4,5 soma",
            "budget budget budget 999999999", "€€€€€€€ ££££ $$$$ 500",
            "180 cm 200 cm 55 inch 100% pamuk 2026 godina 0912345678",
            "dnevni boravak spavaća kuhinja kupaonica hodnik ured blagovaonica sve odjednom",
            "SELECT * FROM products WHERE price < 1000; DROP TABLE users;",
            "<script>alert('kuhinja')</script> 2000 eur",
            "{\"room\":\"kitchen\",\"budget\":2000}",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "kkkkkkk eeeee uuuuu rrrrr", "asdlkfjaslkdfj qwepoiru zxcvmnb",
            "ne želim ikea ali želim ikea ali ne ikea nego jysk ali ne jysk",
            "kuhinja ne kuhinja da kuhinja ne kuhinja",
            "cheap expensive cheap expensive luxury budget premium basic complete",
            "moderno klasično minimalistički industrijski boho toplo svijetlo sve",
            "tamno crno bijelo sivo zeleno plavo žuto crveno roza",
            "perilica hladnjak pećnica napa mikrovalna klima televizor laptop",
            "ikea jysk pevex emmezeta lesnina decathlon sve trgovine ni jedna",
            "gimme a sofa 4 my crib on da cheap ya feel me fam 500 bucks",
            "き ち ん kitchen кухня cocina cuisine küche keittiö kjøkken",
            "not without not with not not without ikea",
            "жиевтино najjeftinije що найдешевше 最も安い billigst",
            "pas cher pas trop moderne mais pas classique sans ikea",
            "budget:::::2000;;;;room====kitchen&&&&style++++modern",
            "🛁🚿🚽 kupaonica bez kade ali s tušem ne bez umivaonika",
            "1.5k 2k 3k 10k 100k 1000k 0.5k .5k k5",
            "\t\tspavaća\t\tsoba\t\t1200\t\t€\t\t", "sPaVaĆa SoBa Do 1200 €",
            "ne previše ne baš previše nije pretjerano previše moderno",
            "‮ehcuk‬ 2000", "cҝ𝗎𝗁𝗂𝗇𝗃𝖺 2000",
            "trosjed za 1.500,00 kn ili 200 € ili £180 ili 9000 kr ili $1200");

    private List<String> corpus() {
        List<String> out = new ArrayList<>(CHAOS);
        // add every 10.190 fixture-ish phrase back too, so a robustness run also re-exercises the real paths
        out.addAll(List.of(
                "ne želim kompletnu kuhinju, samo pećnicu", "nicht ohne IKEA", "ne previše moderno",
                "malo jeftinije", "što jeftinije", "neću da bude jeftino", "amueblar la cocina entera"));
        // programmatic mutations: repetition, casing, concatenation of random chaos pieces
        Random rng = new Random(190190L);
        List<String> seeds = new ArrayList<>(CHAOS);
        for (int i = 0; i < 220; i++) {
            StringBuilder sb = new StringBuilder();
            int parts = 1 + rng.nextInt(4);
            for (int p = 0; p < parts; p++) {
                String piece = seeds.get(rng.nextInt(seeds.size()));
                switch (rng.nextInt(4)) {
                    case 0 -> sb.append(piece.toUpperCase());
                    case 1 -> sb.append(piece.repeat(1 + rng.nextInt(3)));
                    case 2 -> sb.append(new StringBuilder(piece).reverse());
                    default -> sb.append(piece);
                }
                sb.append(rng.nextBoolean() ? ", " : " i ");
            }
            out.add(sb.toString());
        }
        // one genuinely enormous input (bounded by normalized()'s 4000-char cap, but prove it)
        out.add("ne ".repeat(5000) + "kuhinja 2000 eur");
        out.add("kuhinja ".repeat(3000));
        return out;
    }

    @Test
    void extractorNeverCrashesAndAlwaysReturnsSaneFields() {
        List<String> corpus = corpus();
        int m = 0;
        for (String prompt : corpus) {
            String market = MARKETS.get(m++ % MARKETS.size());
            PlannerInputDto in = seed(prompt, market);
            PlannerInputDto out = extractCatching(in, prompt);

            assertThat(out).as("enrich returned null for <<%s>>", trim(prompt)).isNotNull();
            assertThat(out.roomType()).as("room for <<%s>>", trim(prompt)).isIn(ALLOWED_ROOMS);
            assertThat(out.style()).as("style for <<%s>>", trim(prompt)).isIn(ALLOWED_STYLES);
            assertThat(out.furnishingLevel()).as("level for <<%s>>", trim(prompt)).isIn(ALLOWED_LEVELS);
            assertThat(out.optimizationGoal()).as("goal for <<%s>>", trim(prompt)).isIn(ALLOWED_GOALS);
            assertThat(out.budget()).as("budget for <<%s>>", trim(prompt)).isBetween(1, 100_000_000);
            assertThat(out.size()).as("size for <<%s>>", trim(prompt)).isBetween(8, 60);
            assertThat(out.maxStores()).as("maxStores for <<%s>>", trim(prompt)).isGreaterThanOrEqualTo(0);
            // no list a downstream consumer reads may ever be null
            assertThat(out.mustHaveCategories()).isNotNull();
            assertThat(out.alreadyHaveCategories()).isNotNull();
            assertThat(out.colorPreferences()).isNotNull();
            assertThat(out.materialPreferences()).isNotNull();
            assertThat(out.secondaryStyles()).isNotNull();
            assertThat(out.preferredRetailers()).isNotNull();
            assertThat(out.excludedRetailers()).isNotNull();
            assertThat(out.selectedRetailers()).as("selected for <<%s>>", trim(prompt)).isNotEmpty();
            // a secondary style, when present, must itself be a real style
            assertThat(out.secondaryStyles()).allSatisfy(s -> assertThat(s).isIn(ALLOWED_STYLES));
        }
    }

    @Test
    void kitchenClassifierAndAmountParserAndNegationScopeNeverCrash() {
        for (String prompt : corpus()) {
            assertThatCode(() -> {
                KitchenIntentClassifier.KitchenBrief brief = kitchen.classify(prompt);
                assertThat(brief).isNotNull();
                assertThat(brief.intent()).isNotNull();
                assertThat(brief.shape()).isNotNull();
            }).as("classify <<%s>>", trim(prompt)).doesNotThrowAnyException();

            assertThatCode(() -> AmountParser.parseBudget(prompt))
                    .as("parseBudget <<%s>>", trim(prompt)).doesNotThrowAnyException();
            AmountParser.parseBudget(prompt).ifPresent(v ->
                    assertThat(v).as("budget value for <<%s>>", trim(prompt)).isBetween(1, 100_000_000));

            assertThatCode(() -> {
                NegationScope scope = NegationScope.of(prompt);
                // querying wild offsets must be safe
                for (int idx : new int[]{-1000, -1, 0, 7, prompt.length(), Integer.MAX_VALUE}) {
                    scope.isNegated(idx);
                    scope.isDoubleNegated(idx);
                }
            }).as("NegationScope <<%s>>", trim(prompt)).doesNotThrowAnyException();
        }
    }

    private PlannerInputDto extractCatching(PlannerInputDto in, String prompt) {
        try {
            return extractor.enrich(in);
        } catch (RuntimeException e) {
            throw new AssertionError("enrich threw " + e.getClass().getSimpleName() + " for <<" + trim(prompt) + ">>", e);
        }
    }

    private PlannerInputDto seed(String prompt, String market) {
        return new PlannerInputDto(prompt, 1500, "living-room", "bright", "Zagreb", 20, "multi",
                List.of("IKEA", "JYSK", "Pevex", "Emmezeta", "Decathlon", "Lesnina"),
                "best-value", "comfort", List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                List.of(), List.of(), market);
    }

    private static String trim(String s) {
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 60 ? oneLine.substring(0, 60) + "…" : oneLine;
    }
}
