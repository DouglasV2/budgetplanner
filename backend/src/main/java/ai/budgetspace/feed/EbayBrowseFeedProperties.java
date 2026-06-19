package ai.budgetspace.feed;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Sprint 10.51 — configuration for the real eBay Browse API marketplace feed ({@link EbayBrowseFeed}).
 * Read from the environment at deploy time; <strong>no credential, token or endpoint is ever committed.</strong>
 *
 * <p>The feed stays dormant (imports nothing, makes no network call) until an operator supplies an
 * eBay developer App ID + Cert ID through the environment:</p>
 * <pre>
 *   budgetspace.marketplace-feeds.ebay.client-id      = &lt;eBay OAuth App ID / Client ID&gt;
 *   budgetspace.marketplace-feeds.ebay.client-secret  = &lt;eBay Cert ID / Client Secret&gt;
 *   budgetspace.marketplace-feeds.ebay.environment    = production | sandbox   (default: production)
 *   budgetspace.marketplace-feeds.ebay.markets        = DE,IT,AT,FR,NL,ES      (default: all eBay markets)
 *   budgetspace.marketplace-feeds.ebay.furniture-category-id = 3197            (eBay "Furniture" category)
 *   budgetspace.marketplace-feeds.ebay.limit          = 24                     (results per market, 1..200)
 * </pre>
 *
 * <p>eBay runs local marketplaces only in a subset of BudgetSpace's markets. The Croatian/Slovenian/Finnish/
 * Nordic/Slovak/Portuguese markets have no eBay site, so they are intentionally excluded here and keep their
 * own marketplace placeholders (Njuškalo, Bolha, Tori, Finn, Blocket, DBA, Bazoš, OLX) awaiting a partner feed.</p>
 */
@Component
public class EbayBrowseFeedProperties {

    private static final String PREFIX = "budgetspace.marketplace-feeds.ebay.";

    /**
     * The BudgetSpace markets where eBay actually runs a local marketplace (each maps to a Browse
     * {@code EBAY_<CC>} marketplace id). This is the hard allow-list — an override that names a market eBay
     * does not serve (e.g. HR) is ignored, so we never claim eBay coverage where it has no site.
     */
    static final List<String> SUPPORTED_MARKETS = List.of("DE", "IT", "AT", "FR", "NL", "ES", "GB");

    /** eBay's "Home & Garden &gt; Furniture" category. Configurable because ids can drift per site/over time. */
    private static final String DEFAULT_FURNITURE_CATEGORY_ID = "3197";
    private static final int DEFAULT_LIMIT = 24;

    private final Environment environment;

    public EbayBrowseFeedProperties(Environment environment) {
        this.environment = environment;
    }

    public String clientId() {
        return prop("client-id");
    }

    public String clientSecret() {
        return prop("client-secret");
    }

    /** True only when both the App ID (client id) and Cert ID (client secret) are supplied via the env. */
    public boolean isConfigured() {
        return !clientId().isBlank() && !clientSecret().isBlank();
    }

    /** The eBay API base host — production by default, sandbox only when explicitly requested. */
    public String apiBaseUrl() {
        return "sandbox".equalsIgnoreCase(prop("environment"))
                ? "https://api.sandbox.ebay.com"
                : "https://api.ebay.com";
    }

    /** The eBay furniture category id used to scope the Browse search (so we never need a per-language query). */
    public String furnitureCategoryId() {
        String configured = prop("furniture-category-id");
        return configured.isBlank() ? DEFAULT_FURNITURE_CATEGORY_ID : configured;
    }

    /** Results requested per market, clamped to eBay's 1..200 Browse limit. */
    public int limitPerMarket() {
        String raw = prop("limit");
        if (raw.isBlank()) return DEFAULT_LIMIT;
        try {
            return Math.max(1, Math.min(200, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            return DEFAULT_LIMIT;
        }
    }

    /**
     * The markets to query — the configured subset of {@link #SUPPORTED_MARKETS}, or all of them by default.
     * Any configured code eBay does not serve is dropped (honest coverage), and order/dedup follows the
     * supported list.
     */
    public List<String> markets() {
        String configured = prop("markets");
        if (configured.isBlank()) return SUPPORTED_MARKETS;
        List<String> requested = Arrays.stream(configured.split("[,;|]"))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
        List<String> resolved = new ArrayList<>();
        for (String market : SUPPORTED_MARKETS) {
            if (requested.contains(market) && !resolved.contains(market)) {
                resolved.add(market);
            }
        }
        return resolved.isEmpty() ? SUPPORTED_MARKETS : resolved;
    }

    private String prop(String key) {
        return environment.getProperty(PREFIX + key, "").trim();
    }
}
