package ai.budgetspace.pricewatch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.163 (sourcing-policy guard) — the live price probe must send an HONEST, self-identifying User-Agent,
 * never a forged browser one. docs/sourcing-policy.md §1/§6 forbids browser impersonation; a regression back to a
 * "Mozilla/... Chrome/..." UA would violate our own policy, so pin it here.
 */
class HttpLivePriceProbeTest {

    @Test
    void userAgentIsHonestAndNotABrowserString() {
        String ua = HttpLivePriceProbe.USER_AGENT;
        assertThat(ua).doesNotContain("Mozilla").doesNotContain("Chrome").doesNotContain("Safari");
        // ...and it does identify us (so a site owner can see who is fetching).
        assertThat(ua).contains("BudgetSpace");
    }
}
