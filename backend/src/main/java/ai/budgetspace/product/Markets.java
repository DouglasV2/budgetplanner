package ai.budgetspace.product;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sprint 10.13 (#3) — EU market support. Each market is a country where the retailers operate; it
 * maps to a currency and a locale used for price formatting. {@code HR} is the default. Markets are
 * limited to where IKEA/JYSK (and the regional retailers) actually sell; expand the map as catalogs
 * are added for new countries.
 */
public final class Markets {
    public static final String DEFAULT_MARKET = "HR";

    public record MarketInfo(String code, String currency, String locale) {
    }

    // Currencies are accurate as of 2026-06; Croatia/Slovenia/Austria/Germany/Italy/Finland use EUR,
    // others use their national currency.
    private static final Map<String, MarketInfo> MARKETS = Map.ofEntries(
            Map.entry("HR", new MarketInfo("HR", "EUR", "hr-HR")),
            Map.entry("SI", new MarketInfo("SI", "EUR", "sl-SI")),
            Map.entry("AT", new MarketInfo("AT", "EUR", "de-AT")),
            Map.entry("DE", new MarketInfo("DE", "EUR", "de-DE")),
            Map.entry("IT", new MarketInfo("IT", "EUR", "it-IT")),
            Map.entry("FI", new MarketInfo("FI", "EUR", "fi-FI")),
            // Sprint 10.35: France — EUR, verified IKEA catalog.
            Map.entry("FR", new MarketInfo("FR", "EUR", "fr-FR")),
            // Sprint 10.37: Netherlands — EUR, verified IKEA + JYSK catalog.
            Map.entry("NL", new MarketInfo("NL", "EUR", "nl-NL")),
            // Sprint 10.38: Slovakia — EUR, verified IKEA + JYSK catalog (Slovak language = sk, ≠ SI/sl).
            Map.entry("SK", new MarketInfo("SK", "EUR", "sk-SK")),
            // Sprint 10.39: Spain — EUR, verified IKEA catalog (IKEA-only; no JYSK in ES).
            Map.entry("ES", new MarketInfo("ES", "EUR", "es-ES")),
            Map.entry("PL", new MarketInfo("PL", "PLN", "pl-PL")),
            Map.entry("CZ", new MarketInfo("CZ", "CZK", "cs-CZ")),
            Map.entry("HU", new MarketInfo("HU", "HUF", "hu-HU")),
            Map.entry("RO", new MarketInfo("RO", "RON", "ro-RO")),
            Map.entry("SE", new MarketInfo("SE", "SEK", "sv-SE")),
            Map.entry("DK", new MarketInfo("DK", "DKK", "da-DK"))
    );

    private Markets() {
    }

    public static String normalize(String market) {
        if (market == null || market.isBlank()) return DEFAULT_MARKET;
        String code = market.trim().toUpperCase(Locale.ROOT);
        return MARKETS.containsKey(code) ? code : DEFAULT_MARKET;
    }

    public static boolean isKnown(String market) {
        return market != null && MARKETS.containsKey(market.trim().toUpperCase(Locale.ROOT));
    }

    public static String currencyFor(String market) {
        return MARKETS.getOrDefault(normalize(market), MARKETS.get(DEFAULT_MARKET)).currency();
    }

    public static String localeFor(String market) {
        return MARKETS.getOrDefault(normalize(market), MARKETS.get(DEFAULT_MARKET)).locale();
    }

    public static List<MarketInfo> all() {
        return MARKETS.values().stream()
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();
    }
}
