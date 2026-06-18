package ai.budgetspace.feed;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Sprint 10.49 — marketplace feed configuration, read from the environment at deploy time.
 * <strong>No credentials, tokens or endpoints are ever committed.</strong> Every marketplace defaults to
 * blank ("not configured"), so the app starts and runs identically whether or not a feed exists.
 *
 * <p>A marketplace feed is configured when its URL property is non-blank, e.g.
 * {@code budgetspace.marketplace-feeds.ebay.url} or {@code budgetspace.marketplace-feeds.njuskalo.url}.
 * The real feed client reads its own secret (API key/token) from its own env var when it is built — we do
 * not read or store any secret here. Lookup is dynamic so adding a marketplace placeholder needs no code
 * change here.</p>
 */
@Component
public class MarketplaceFeedProperties {

    private final Environment environment;

    public MarketplaceFeedProperties(Environment environment) {
        this.environment = environment;
    }

    /** The configured feed URL for a marketplace, or an empty string when none is set. */
    public String feedUrlFor(String marketplace) {
        if (marketplace == null) return "";
        String key = marketplace.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return environment.getProperty("budgetspace.marketplace-feeds." + key + ".url", "").trim();
    }

    /** True when a non-blank feed URL has been supplied for the marketplace. */
    public boolean isConfigured(String marketplace) {
        return !feedUrlFor(marketplace).isBlank();
    }
}
