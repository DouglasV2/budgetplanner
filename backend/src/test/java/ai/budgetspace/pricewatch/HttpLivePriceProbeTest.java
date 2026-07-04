package ai.budgetspace.pricewatch;

import ai.budgetspace.pricewatch.LivePriceProbe.Liveness;
import org.junit.jupiter.api.Test;

import java.net.URI;

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

    // --- Sprint 10.167: dead-link classification (deterministic, no network). Cases mirror the real signals
    // observed live: IKEA discontinued products bounce to /cat/, others redirect up to a parent category, and a
    // re-slug that preserves the product id (or lands on an equally-deep sibling) is still LIVE — never retired.

    private static Liveness classify(String requested, String landed, String retailer) {
        return HttpLivePriceProbe.classifyLanding(URI.create(requested), URI.create(landed), retailer);
    }

    @Test
    void sameUrlIsLive() {
        assertThat(classify("https://jysk.dk/stue/sofaer/sofa-falslev-2-pers",
                "https://jysk.dk/stue/sofaer/sofa-falslev-2-pers", "JYSK")).isEqualTo(Liveness.LIVE);
    }

    @Test
    void ikeaBounceToCategoryIsDead() {
        assertThat(classify("https://www.ikea.com/si/sl/p/dytag-zavese-20466720/",
                "https://www.ikea.com/si/sl/cat/izdelki-products/", "IKEA")).isEqualTo(Liveness.DEAD);
    }

    @Test
    void ikeaSluglessUrlRedirectingToRealProductIsLive() {
        assertThat(classify("https://www.ikea.com/fi/fi/p/-80576786/",
                "https://www.ikea.com/fi/fi/p/skogsduva-tyyny-valkoinen-80576786/", "IKEA")).isEqualTo(Liveness.LIVE);
    }

    @Test
    void redirectUpToParentCategoryIsDead() {
        assertThat(classify("https://jysk.dk/stue/laenestole/laenestol-simested-sort-beige",
                "https://jysk.dk/stue/laenestole", "JYSK")).isEqualTo(Liveness.DEAD);
    }

    @Test
    void reSlugKeepingTheProductPathIsLive() {
        // JYSK moved the product to a shorter category tree but kept the product slug — still the same product.
        assertThat(classify("https://jysk.hr/blagovaonica/komode-i-vitrine/komode/komoda-saltvig-3-vrata-jasen",
                "https://jysk.hr/blagovaonica/komode/komoda-saltvig-3-vrata-jasen", "JYSK")).isEqualTo(Liveness.LIVE);
    }

    @Test
    void ssrfGuardBlocksPrivateAndLoopbackHostsButAllowsPublicLiterals() {
        // SSRF guard: the probe must refuse loopback / link-local (incl. cloud metadata) / private RFC-1918 hosts.
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://127.0.0.1/x"))).isTrue();
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://localhost/x"))).isTrue();
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://169.254.169.254/latest/meta-data/"))).isTrue();
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://10.0.0.5/x"))).isTrue();
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://192.168.1.10/x"))).isTrue();
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://172.16.0.1/x"))).isTrue();
        // A public literal IP is allowed (no DNS needed; 8.8.8.8 is a public address).
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://8.8.8.8/x"))).isFalse();
    }

    @Test
    void reSlugKeepingTheProductIdIsLiveButADifferentIdIsDead() {
        // Same trailing product id (4320261) across a slug change = same product = LIVE.
        assertThat(classify("https://www.kwantum.nl/eettafel-ferrara-walnoot-4320261",
                "https://www.kwantum.nl/eettafel-ferrara-walnootlook-4320261", "Kwantum")).isEqualTo(Liveness.LIVE);
        // Bounced to a DIFFERENT product id (the requested one is gone) = DEAD.
        assertThat(classify("https://www.kwantum.nl/salontafel-ferrara-walnoot-4323413",
                "https://www.kwantum.nl/eettafel-ferrara-walnootlook-4320261", "Kwantum")).isEqualTo(Liveness.DEAD);
    }
}
