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
        // Sprint 10.167 sweep: also block CGNAT (100.64.0.0/10) and IPv6 Unique-Local (fc00::/7).
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://100.64.0.1/x"))).isTrue();
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://100.127.255.254/x"))).isTrue();
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://[fd00:ec2::254]/x"))).isTrue();
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://[fc00::1]/x"))).isTrue();
        // Public literals still allowed (100.63.x is NOT CGNAT; 8.8.8.8 public).
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://8.8.8.8/x"))).isFalse();
        assertThat(HttpLivePriceProbe.isBlockedHost(URI.create("http://100.63.0.1/x"))).isFalse();
    }

    @Test
    void singleSegmentReSlugIsLiveOnlyARootBounceIsDead() {
        // Sprint 10.167 sweep: a flat-URL retailer (e.g. Harvey Norman) re-slugging /trosjed-filip -> /trosjed-filip-v2
        // must stay LIVE (was wrongly retired by the old "<=1 segment => DEAD" rule). Only a bounce to root is DEAD.
        assertThat(classify("https://www.harveynorman.hr/trosjed-filip",
                "https://www.harveynorman.hr/trosjed-filip-v2", "Harvey Norman")).isEqualTo(Liveness.LIVE);
        assertThat(classify("https://www.harveynorman.hr/tv-element-rivero-189",
                "https://www.harveynorman.hr/tv-element-rivero-190", "Harvey Norman")).isEqualTo(Liveness.LIVE);
        // A genuine bounce to the site root is still DEAD.
        assertThat(classify("https://www.harveynorman.hr/trosjed-filip",
                "https://www.harveynorman.hr/", "Harvey Norman")).isEqualTo(Liveness.DEAD);
    }

    // --- Sprint 10.193: the price shapes the probe was blind to, found by re-probing the shipped catalog.

    @Test
    void productGroupOfferIsReadNotOnlyProduct() {
        // jysk.at/dk/fi/fr/it/pt publish the page price ONLY under @type ProductGroup. A Product-only reader
        // finds nothing there, so those rows could never be re-verified and sat at check-store forever.
        String html = """
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"ProductGroup","name":"Schaummatratze WELLPUR",
                 "offers":{"@type":"Offer","url":"/schlafzimmer/matratzen/schaummatratze-160x200cm-wellpur-kvita-fest-0",
                 "priceCurrency":"EUR","price":799}}
                </script>""";
        assertThat(new HttpLivePriceProbe().jsonLdPrice(html, null))
                .contains(new java.math.BigDecimal("799"));
    }

    @Test
    void theVariantMatchingTheRequestedPageWinsOverTheGroupPrice() {
        // A ProductGroup lists every size; only the variant whose offer URL is the page we asked for is OUR row.
        String html = """
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"ProductGroup","name":"Sofa FALSLEV",
                 "offers":{"@type":"Offer","priceCurrency":"EUR","price":499},
                 "hasVariant":[
                   {"@type":"Product","offers":{"@type":"Offer","url":"/stue/sofaer/sofa-falslev-2-pers","price":399}},
                   {"@type":"Product","offers":{"@type":"Offer","url":"/stue/sofaer/sofa-falslev-3-pers","price":599}}]}
                </script>""";
        assertThat(new HttpLivePriceProbe().jsonLdPrice(html, "/stue/sofaer/sofa-falslev-3-pers"))
                .contains(new java.math.BigDecimal("599"));
    }

    @Test
    void aShortenedSlugIsAReSlugNotACategoryBounce() {
        // jysk.se truncated the slug (…-natur-vildekfargat -> …-natur-vildek). The landed path is a STRING
        // prefix of the requested one but sits at the same depth, so it is the same product, not a category.
        assertThat(classify("https://jysk.se/sovrum/sangbord/sangbord-limfjorden-2-lador-natur-vildekfargat",
                "https://jysk.se/sovrum/sangbord/sangbord-limfjorden-2-lador-natur-vildek", "JYSK"))
                .isEqualTo(Liveness.LIVE);
    }

    @Test
    void aReSlugThatAppendsAVariantIdKeepsTheRequestedId() {
        // Bygghemma re-slugs /p-175779 -> /p-175779-175783 (same product, variant id appended). The trailing-id
        // rule saw two DIFFERENT ids and retired a live product.
        assertThat(classify("https://www.bygghemma.se/kok-och-bad/dusch/duschkabin-hafa-polaris-square/p-175779",
                "https://www.bygghemma.se/kok-och-bad/dusch/duschkabin-hafa-polaris-square/p-175779-175783",
                "Bygghemma")).isEqualTo(Liveness.LIVE);
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
