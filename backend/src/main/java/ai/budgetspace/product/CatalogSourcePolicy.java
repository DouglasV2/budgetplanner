package ai.budgetspace.product;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 10.14 — the single source of truth for <strong>how a retailer may be sourced</strong> and
 * <strong>what counts as a production-verified product</strong>.
 *
 * <p>Architectural rule (do not work around it): a retailer that blocks automated access (e.g. an
 * HTTP&nbsp;403 / WAF bot block on the homepage) is <em>not</em> solved by bypassing the protection.
 * We never rotate proxies, spoof a browser fingerprint, reuse cookies/sessions or call private
 * search/stock/Algolia endpoints. Instead the sourcing strategy changes: such a retailer is marked
 * {@link SourcingStatus#OFFICIAL_FEED_REQUIRED} and only an official/partner feed (or a hand-verified
 * product) may ever populate it. See {@code docs/sourcing-policy.md}.</p>
 *
 * <p>Provenance of every imported product is recorded in {@code Product.sourceType}. The canonical
 * provenance values are:</p>
 * <ul>
 *   <li>{@link #SOURCE_MANUAL_VERIFIED} — a human verified name/price/URL by hand,</li>
 *   <li>{@link #SOURCE_PUBLIC_PRODUCT_PAGE} — verified from a publicly reachable product page,</li>
 *   <li>{@link #SOURCE_OFFICIAL_FEED} — delivered by the retailer's official/partner feed,</li>
 *   <li>{@link #SOURCE_AFFILIATE_FEED} — delivered by an affiliate network feed.</li>
 * </ul>
 * (The pre-10.14 values {@code manual}, {@code retailer-snapshot} and {@code future-scraper} remain
 * valid; {@code retailer-snapshot} is the historical equivalent of {@link #SOURCE_PUBLIC_PRODUCT_PAGE}.)
 */
public final class CatalogSourcePolicy {

    /** How a retailer is allowed to be sourced. */
    public enum SourcingStatus {
        /** Public product pages are reachable and hand-verified; live fetch + manual import allowed (IKEA, JYSK). */
        DIRECT_VERIFIED,
        /** Hand-verified, link-out only — no automated fetch (Emmezeta shows no prices/ratings to scrape). */
        MANUAL_VERIFIED_ONLY,
        /** Homepage returns 403 / is WAF-blocked. Direct import is forbidden; needs an official/partner feed. */
        OFFICIAL_FEED_REQUIRED
    }

    // Canonical import-source provenance values (mirrors the product direction's MANUAL_VERIFIED etc.).
    public static final String SOURCE_MANUAL_VERIFIED = "manual-verified";
    public static final String SOURCE_PUBLIC_PRODUCT_PAGE = "public-product-page";
    public static final String SOURCE_OFFICIAL_FEED = "official-feed";
    public static final String SOURCE_AFFILIATE_FEED = "affiliate-feed";
    /**
     * Sprint 10.21: a used item from a consumer marketplace (Njuškalo, Facebook Marketplace), delivered
     * by a compliant feed/API — never scraped. Distinct from {@link #SOURCE_OFFICIAL_FEED} because its
     * trust/freshness/UI handling differs (single-unit, ephemeral, "used"). See docs/marketplace-sourcing.md.
     */
    public static final String SOURCE_MARKETPLACE_LISTING = "marketplace-listing";

    /** Source types that mean the product was delivered by a configured feed (retailer/affiliate/marketplace). */
    public static final Set<String> FEED_SOURCE_TYPES = Set.of(SOURCE_OFFICIAL_FEED, SOURCE_AFFILIATE_FEED, SOURCE_MARKETPLACE_LISTING);

    // Per-retailer sourcing status. Re-confirmed 2026-06-16: decathlon.hr, pevex.hr and xxxlesnina.hr
    // return HTTP 403 even on the homepage (edge/WAF bot block, not auth), so they are feed-required.
    private static final Map<String, SourcingStatus> STATUS_BY_RETAILER = buildStatusMap();

    private CatalogSourcePolicy() {
    }

    private static Map<String, SourcingStatus> buildStatusMap() {
        LinkedHashMap<String, SourcingStatus> map = new LinkedHashMap<>();
        map.put("IKEA", SourcingStatus.DIRECT_VERIFIED);
        map.put("JYSK", SourcingStatus.DIRECT_VERIFIED);
        map.put("Emmezeta", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Decathlon", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Pevex", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Lesnina", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        // Sprint 10.16: reachable + hand-verified (link-out; have products in the catalog).
        map.put("Harvey Norman", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Namjestaj.hr", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Otto", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Segmüller", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Poco", SourcingStatus.MANUAL_VERIFIED_ONLY);
        // Sprint 10.36: France — reachable + price in static HTML (JSON-LD / visible €), web-verified per product.
        map.put("Camif", SourcingStatus.MANUAL_VERIFIED_ONLY);
        // Sprint 10.43: Spain — Kenay Home + Banak Importa serve static prices on product pages (verified).
        map.put("Kenay Home", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Banak Importa", SourcingStatus.MANUAL_VERIFIED_ONLY);
        // Sprint 10.44: Netherlands — Leen Bakker + Kwantum (static-priced product pages, verified).
        map.put("Leen Bakker", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Kwantum", SourcingStatus.MANUAL_VERIFIED_ONLY);
        // Sprint 10.45: depth — Moviflor (PT) + Nábytok (SK) serve static prices + og:image (verified).
        map.put("Moviflor", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Nábytok", SourcingStatus.MANUAL_VERIFIED_ONLY);
        // Sprint 10.48: retail re-sweep — verified static-priced retailers (JSON-LD / PrestaShop / Shopify / €).
        map.put("Svijetnamještaja", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Svetpohištva", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Interio", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Masku", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Lovely Meubles", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("JOM", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Sítio do Móvel", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Miroytengo", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Merkamueble", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Muebles BOOM", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Pronto Wonen", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("Drevona", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("ASKO Nábytok", SourcingStatus.MANUAL_VERIFIED_ONLY);
        // Re-confirmed 2026-06-16: blocked (403/anti-bot/unreachable) or unusable for direct import
        // (JS-only prices / out-of-scope catalog) → only an official/partner feed may populate them.
        map.put("Momax", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Prima Namještaj", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Perfecta Dreams", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Bauhaus", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("FeroTerm", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Merkur", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Dipo", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Wayfair", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Home24", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Roller", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Kika", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Leiner", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("XXXLutz", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        // Sprint 10.36: major French chains probed 2026-06-18 — anti-bot (DataDome/Cloudflare 403) or
        // JS-only. Not bypassed → feed-required (Camif above is the one directly-verifiable FR retailer).
        // Sprint 10.48: conforama.IT serves JSON-LD prices (verified, has IT products); conforama.FR stays anti-bot.
        map.put("Conforama", SourcingStatus.MANUAL_VERIFIED_ONLY);
        map.put("But", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Maisons du Monde", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("La Redoute", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Fly", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Habitat", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Cdiscount", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Vente-unique", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        // Sprint 10.43: Spain — homepage reachable but product pages reset the connection (anti-bot).
        map.put("Muebles La Fabrica", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        // Sprint 10.45: Finland — Sotka (sotka.fi) renders product prices client-side (JS-only) → feed-required.
        map.put("Sotka", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        // Sprint 10.21: second-hand consumer marketplaces. ToS-protected, no open product API, anti-bot —
        // populated only by a compliant official/partner feed (sourceType=marketplace-listing), never
        // scraped. See docs/marketplace-sourcing.md.
        map.put("Njuškalo", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        map.put("Facebook Marketplace", SourcingStatus.OFFICIAL_FEED_REQUIRED);
        // Sprint 10.49: per-country second-hand marketplace placeholders — feed/affiliate-ready, never scraped.
        for (String marketplace : new String[] {
                "eBay", "Bolha", "Willhaben", "Kleinanzeigen", "Subito", "Tori", "Leboncoin", "Marktplaats",
                "Bazoš", "Wallapop", "OLX", "Finn", "Blocket", "DBA" }) {
            map.put(marketplace, SourcingStatus.OFFICIAL_FEED_REQUIRED);
        }
        return Map.copyOf(map);
    }

    /**
     * The sourcing status for a retailer. Unknown / unmapped retailers default to
     * {@link SourcingStatus#OFFICIAL_FEED_REQUIRED} so we never fetch something we have not vetted.
     */
    public static SourcingStatus statusFor(String retailer) {
        String key = ProductTaxonomy.normalizeRetailer(retailer).orElse(retailer == null ? "" : retailer.trim());
        return STATUS_BY_RETAILER.getOrDefault(key, SourcingStatus.OFFICIAL_FEED_REQUIRED);
    }

    /** True when the retailer's homepage/pages are blocked and only a feed may populate it. */
    public static boolean isFeedRequired(String retailer) {
        return statusFor(retailer) == SourcingStatus.OFFICIAL_FEED_REQUIRED;
    }

    /** True only for retailers whose public product pages we are allowed to fetch directly. */
    public static boolean isDirectFetchAllowed(String retailer) {
        return statusFor(retailer) == SourcingStatus.DIRECT_VERIFIED;
    }

    /** Retailers that must not be scraped/collected and require an official or partner feed. */
    public static List<String> feedRequiredRetailers() {
        return STATUS_BY_RETAILER.entrySet().stream()
                .filter(entry -> entry.getValue() == SourcingStatus.OFFICIAL_FEED_REQUIRED)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** True when the source type means the product came from a configured official/affiliate feed. */
    public static boolean isFeedSourceType(String sourceType) {
        return sourceType != null && FEED_SOURCE_TYPES.contains(sourceType.trim().toLowerCase(Locale.ROOT));
    }

    /** A short, honest explanation an operator/log can show for the retailer's status. */
    public static String reasonFor(String retailer) {
        return switch (statusFor(retailer)) {
            case DIRECT_VERIFIED -> "Javne product stranice su dohvatljive i ručno provjerene — dopušten kontrolirani import.";
            case MANUAL_VERIFIED_ONLY -> "Samo ručno provjereni proizvodi s link-outom — bez automatiziranog dohvaćanja.";
            case OFFICIAL_FEED_REQUIRED -> "Naslovnica/stranice vraćaju HTTP 403 (WAF/anti-bot). Ne zaobilazimo zaštitu — "
                    + "uvoz je moguć samo preko službenog/partnerskog feeda ili ručno provjerenih proizvoda.";
        };
    }

    /**
     * The production / verified-catalog gate. A product is production-verified only when:
     * <ol>
     *   <li>it can enter the planner at all ({@link ProductTaxonomy#canEnterPlanner} — in stock, priced,
     *       has a room + style, and is <em>not</em> {@code needs-review}),</li>
     *   <li>it is not {@link ProductTaxonomy#isStale stale} (price/availability recently checked),</li>
     *   <li>it carries a real {@code sourceReference} — legacy {@code data.sql} sample rows have none, so
     *       they are excluded, and</li>
     *   <li>if its retailer is {@link SourcingStatus#OFFICIAL_FEED_REQUIRED}, it actually came from an
     *       official/affiliate feed (never from scraping a blocked site).</li>
     * </ol>
     * NEEDS_REVIEW, STALE and sample products therefore never count as verified.
     */
    public static boolean isProductionVerified(Product product) {
        return isPlannerEligible(product)
                && !ProductTaxonomy.isStale(product == null ? null : product.getLastCheckedAt());
    }

    /**
     * Sprint 10.21+ — the gate the <strong>planner</strong> uses to pick products: everything
     * {@link #isProductionVerified} requires <em>except freshness</em>. A {@link ProductTaxonomy#isStale
     * stale} row still enters the plan (the UI shows a "provjeri u trgovini" note), so an aging catalog
     * never silently empties; freshness is handled by re-verification cadence, not by hiding products.
     *
     * <p>So this excludes exactly what must never reach a user: legacy {@code data.sql} sample rows (no
     * {@code sourceReference}), {@code needs-review}/unavailable rows ({@link ProductTaxonomy#canEnterPlanner}),
     * and any {@link SourcingStatus#OFFICIAL_FEED_REQUIRED} retailer that did not arrive via a feed
     * (i.e. was never scraped past its block).</p>
     */
    public static boolean isPlannerEligible(Product product) {
        if (product == null) return false;
        if (!ProductTaxonomy.canEnterPlanner(product)) return false;
        if (isBlank(product.getSourceReference())) return false;
        if (statusFor(product.getRetailer()) == SourcingStatus.OFFICIAL_FEED_REQUIRED) {
            return isFeedSourceType(product.getSourceType());
        }
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
