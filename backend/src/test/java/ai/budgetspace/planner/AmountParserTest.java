package ai.budgetspace.planner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.181 — unit tests for the centralized budget/amount parser. Inputs are already in the normalized form
 * {@code PlannerIntentExtractor.normalize} produces (lower-case, accent-stripped: ž→z, š→s, č→c, ć→c). Covers every
 * budget format the spec requires (Phase 6) and the adversarial not-a-budget cases (Phase 10).
 */
class AmountParserTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            // --- plain / currency-attached (EUR) ---
            "dnevni boravak 1000,           1000",
            "dnevni boravak 1000eur,        1000",
            "dnevni boravak 1000 eur,       1000",
            "dnevni boravak 1000 eura,      1000",
            "dnevni boravak 1000e,          1000",
            "dnevni boravak 1.000 eur,      1000",
            "dnevni boravak 1 000 eur,      1000",
            // --- k shorthand ---
            "dnevni boravak 1k,             1000",
            "dnevni boravak 1.5k,           1500",
            "'dnevni boravak 1,5k',         1500",
            "dnevni boravak 2k,             2000",
            "dnevni boravak 10k,            10000",
            // --- budget verbs ---
            "do 1000,                       1000",
            "maks 800,                      800",
            "oko 1500,                      1500",
            "ispod 2000,                    2000",
            // --- number-words / slang ---
            "soma,                          1000",
            "jedan som,                     1000",
            "dva soma,                      2000",
            "pet soma,                      5000",
            "soma i po,                     1500",
            "soma i pol,                    1500",
            "tisucu,                        1000",
            "dvije tisuce,                  2000",
            "hiljadu,                       1000",
            "dvije hiljade,                 2000",
            "oko soma,                      1000",
            "maks soma i pol,               1500",
            "kuhinja do 2 soma,             2000",
            "pola soma,                     500",
            // --- non-EUR currencies (bare number stays in the market's currency) ---
            "9000 kr,                       9000",
            "15000 kr,                      15000",
            // --- the exact spec prompts (budget component) ---
            "e boravak za soma eura,        1000",
            "spavaca 900,                   900",
            "kupaona dva soma treba skoljka i tus kadu necu, 2000",
    })
    void parsesRealBudgets(String text, int expected) {
        assertThat(AmountParser.parseBudget(text)).as(text).contains(expected);
    }

    @Test
    void gbpPrefixAndSymbol() {
        assertThat(AmountParser.parseBudget("home office £1800")).contains(1800);
        assertThat(AmountParser.parseBudget("dnevni boravak €1000")).contains(1000);
    }

    @ParameterizedTest(name = "\"{0}\" -> no budget")
    @CsvSource({
            "6 stolica",                 // quantity, not budget (1 digit)
            "treba mi 6 stolica",        // quantity
            "soba je 20 m2",             // room size
            "prostor 20m2",              // room size glued
            "0912345678",                // phone number
            "narudzba 100200300",        // order number (overflows sane range)
            "planiram u 2026",           // a year is not a budget
            "godina 2030",               // a year
            "dnevni boravak",            // no number at all
    })
    void rejectsNonBudgets(String text) {
        assertThat(AmountParser.parseBudget(text)).as(text).isEmpty();
    }

    @Test
    void emptyNullAndWhitespaceAreSafe() {
        assertThat(AmountParser.parseBudget("")).isEmpty();
        assertThat(AmountParser.parseBudget(null)).isEmpty();
        assertThat(AmountParser.parseBudget("     ")).isEmpty();
        assertThat(AmountParser.parseBudget("!!! ??? ...")).isEmpty();
    }

    @Test
    void hugeNumberDoesNotOverflowOrThrow() {
        // A pathological long digit run must never overflow or throw. The parser may extract a sane sub-amount
        // (a 6-digit chunk) or nothing — either is fine — but it must stay within the sane ceiling and not crash.
        Optional<Integer> r = AmountParser.parseBudget("dnevni boravak 999999999999 eur");
        assertThat(r).hasValueSatisfying(v -> assertThat(v).isBetween(1, 100_000_000));
        assertThat(AmountParser.parseBudget("111111111111111111111111")).isEmpty(); // one unbroken run, no sane token
    }

    @Test
    void yearWithCurrencyIsMoneyNotAYear() {
        // The year guard only applies to a BARE number; "2026 eura" is explicitly money.
        assertThat(AmountParser.parseBudget("do 2026 eura")).contains(2026);
    }

    @Test
    void leftmostExplicitWinsOverLaterNumbers() {
        // an explicit budget is chosen over a trailing bare/quantity number
        assertThat(AmountParser.parseBudget("kupaona 2000 eur, 6 plocica")).contains(2000);
    }
}
