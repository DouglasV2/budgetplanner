package ai.budgetspace.collector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Retailer-specific parser v1 for IKEA (Sprint 9.3). On purpose this stays small: it refines
 * what the generic parser already produced for IKEA pages instead of hardcoding dozens of CSS
 * selectors. The goal is a stable v1 over small URL lists, not a perfect universal scraper.
 *
 * <p>It does two concrete things:</p>
 * <ul>
 *   <li>recognises IKEA pages (by retailer or domain),</li>
 *   <li>generates a stable {@code externalId} from the IKEA article number in the URL, and</li>
 *   <li>cleans the store suffix ("… - IKEA", "… | IKEA") from the product name.</li>
 * </ul>
 */
public class IkeaProductParser {
    // IKEA article numbers are 8 digits, often shown as "302.991.18" or "30299118".
    private static final Pattern ARTICLE_DOTTED = Pattern.compile("(\\d{3})[.\\-](\\d{3})[.\\-](\\d{2})");
    private static final Pattern ARTICLE_PLAIN = Pattern.compile("(?<!\\d)(\\d{8})(?!\\d)");

    public boolean handles(String retailer, String url) {
        if (retailer != null && retailer.equalsIgnoreCase("IKEA")) return true;
        return url != null && url.toLowerCase().contains("ikea.");
    }

    public String refineName(String rawName) {
        if (rawName == null) return null;
        String name = rawName.trim();
        for (String suffix : new String[]{" - IKEA Hrvatska", " - IKEA", " | IKEA", " – IKEA"}) {
            int index = name.toUpperCase().indexOf(suffix.toUpperCase());
            if (index > 0) {
                name = name.substring(0, index).trim();
            }
        }
        return name;
    }

    /** Stable external id from the IKEA article number in the URL, or {@code null} if none. */
    public String articleNumberExternalId(String url) {
        if (url == null) return null;
        Matcher dotted = ARTICLE_DOTTED.matcher(url);
        if (dotted.find()) {
            return "ikea-" + dotted.group(1) + dotted.group(2) + dotted.group(3);
        }
        Matcher plain = ARTICLE_PLAIN.matcher(url);
        if (plain.find()) {
            return "ikea-" + plain.group(1);
        }
        return null;
    }
}
