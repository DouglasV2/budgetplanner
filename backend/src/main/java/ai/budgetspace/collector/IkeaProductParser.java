package ai.budgetspace.collector;

/**
 * Retailer-specific parser skeleton (Sprint 9.1). On purpose this is tiny: it only refines
 * what the generic parser already produced for IKEA pages, instead of hardcoding dozens of
 * CSS selectors. The point of the sprint is a stable structure, not a perfect scrape.
 *
 * <p>IKEA `og:title` / page titles usually carry a store suffix like "… - IKEA" or
 * "… | IKEA"; we strip it so the saved name is clean. Add more retailer-specific rules here
 * later, keeping each one small and covered by a fixture test.</p>
 */
public class IkeaProductParser {

    public String refineName(String rawName) {
        if (rawName == null) return null;
        String name = rawName.trim();
        for (String suffix : new String[]{" - IKEA", " | IKEA", " – IKEA", " - IKEA Hrvatska"}) {
            int index = name.toUpperCase().indexOf(suffix.toUpperCase());
            if (index > 0) {
                name = name.substring(0, index).trim();
            }
        }
        return name;
    }
}
