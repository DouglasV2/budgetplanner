package ai.budgetspace.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.74 — currency facts. The budget ceiling must scale with the currency's denomination so a normal
 * budget in a high-denomination currency (NOK/SEK/DKK) is not wrongly capped at the EUR ceiling.
 */
class MarketsTest {

    @Test
    void budgetCeilingScalesWithCurrencyDenomination() {
        assertThat(Markets.budgetCeiling("EUR")).isEqualTo(9_000);
        assertThat(Markets.budgetCeiling("GBP")).isEqualTo(9_000);
        // High-denomination currencies allow a proportionally larger budget (≈ EUR 9000 of real value).
        assertThat(Markets.budgetCeiling("NOK")).isGreaterThanOrEqualTo(50_000);
        assertThat(Markets.budgetCeiling("SEK")).isGreaterThanOrEqualTo(50_000);
        assertThat(Markets.budgetCeiling("DKK")).isGreaterThanOrEqualTo(30_000);
        // Unknown / null fail safe to the EUR default; matching is case-insensitive.
        assertThat(Markets.budgetCeiling(null)).isEqualTo(9_000);
        assertThat(Markets.budgetCeiling("zzz")).isEqualTo(9_000);
        assertThat(Markets.budgetCeiling("nok")).isEqualTo(Markets.budgetCeiling("NOK"));
    }

    @Test
    void currencyForMapsMarketsAndDefaultsSafely() {
        assertThat(Markets.currencyFor("NO")).isEqualTo("NOK");
        assertThat(Markets.currencyFor("DE")).isEqualTo("EUR");
        assertThat(Markets.currencyFor("GB")).isEqualTo("GBP");
        assertThat(Markets.currencyFor(null)).isEqualTo("EUR");  // default market (HR) is EUR
        assertThat(Markets.currencyFor("ZZ")).isEqualTo("EUR");  // unknown → default
    }
}
