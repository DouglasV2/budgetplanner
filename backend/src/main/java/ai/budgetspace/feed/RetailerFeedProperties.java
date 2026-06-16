package ai.budgetspace.feed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sprint 10.14 — feed configuration, sourced from the environment. <strong>No credentials, tokens or
 * endpoints are ever committed.</strong> Every value defaults to blank, which means "not configured",
 * so the application starts and runs identically whether or not a feed exists.
 *
 * <p>A feed is considered configured when its URL is non-blank. The future real feed client reads its
 * own secret (API key/token) directly from its own environment variable at that time — we deliberately
 * do not read or store any secret here.</p>
 */
@Component
public class RetailerFeedProperties {

    private final Map<String, String> feedUrlByRetailer;

    public RetailerFeedProperties(
            @Value("${budgetspace.feeds.decathlon.url:}") String decathlonUrl,
            @Value("${budgetspace.feeds.pevex.url:}") String pevexUrl,
            @Value("${budgetspace.feeds.lesnina.url:}") String lesninaUrl) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("decathlon", trim(decathlonUrl));
        map.put("pevex", trim(pevexUrl));
        map.put("lesnina", trim(lesninaUrl));
        this.feedUrlByRetailer = Map.copyOf(map);
    }

    /** The configured feed URL for a retailer, or an empty string when none is set. */
    public String feedUrlFor(String retailer) {
        if (retailer == null) return "";
        return feedUrlByRetailer.getOrDefault(retailer.trim().toLowerCase(Locale.ROOT), "");
    }

    /** True when a non-blank feed URL has been supplied for the retailer. */
    public boolean isConfigured(String retailer) {
        return !feedUrlFor(retailer).isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
