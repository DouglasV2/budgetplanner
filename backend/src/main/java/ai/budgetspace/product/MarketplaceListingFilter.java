package ai.budgetspace.product;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/**
 * Sprint 10.21 — the availability guard for second-hand marketplace listings (Njuškalo, Facebook
 * Marketplace). A used listing is gone the moment it sells, so a marketplace feed must be defensive both
 * on ingest and on every refresh. This is the code form of {@code docs/marketplace-sourcing.md §4}:
 *
 * <ul>
 *   <li>{@link #isSoldOrUnavailable} — drop a listing whose title or status says it is sold / reserved /
 *       removed / inactive (case- and accent-insensitive), so we never surface a {@code PRODANO} ad.</li>
 *   <li>{@link #isStale} — marketplace listings expire fast, so the freshness window is
 *       {@link #MARKETPLACE_STALE_AFTER_HOURS} hours (not the 14-day retail window); a listing older than
 *       that, or absent from the latest feed snapshot, must not be shown as live.</li>
 *   <li>{@link #shouldDrop} — the combined ingest test: drop if sold/unavailable OR stale.</li>
 * </ul>
 *
 * <p>Sprint 10.64: eBay is now wired as a LIVE source (transient, never persisted) and runs every candidate row
 * through this filter before surfacing it. The other marketplaces remain
 * {@link CatalogSourcePolicy.SourcingStatus#OFFICIAL_FEED_REQUIRED} placeholders that carry no products, awaiting
 * a compliant feed which would likewise run each row through this filter.</p>
 */
public final class MarketplaceListingFilter {

    /** Marketplace listings are re-checked within this many hours; older = stale (see class doc). */
    public static final int MARKETPLACE_STALE_AFTER_HOURS = 24;

    /**
     * Accent-free, lowercase substrings that mean a listing is no longer purchasable. Matched against a
     * diacritic-stripped, lower-cased haystack, so {@code "PRODANO"}, {@code "Prodano"} and
     * {@code "završeno"} all hit. Extend this list as new marketplaces surface new wording.
     */
    public static final List<String> SOLD_MARKERS = List.of(
            "prodano",        // HR/SI sold
            "sold",           // EN "sold" / "sold out"
            "rezervirano",    // HR/SI reserved
            "reserved",
            "nije dostupno",  // not available
            "nedostupno",
            "neaktivan",      // inactive (listing closed)
            "neaktivno",
            "zavrseno",       // završeno — ended
            "povuceno",       // povučeno — withdrawn
            "uklonjeno",      // removed
            "izbrisano",      // deleted
            "isteklo",        // expired
            "gotovo",         // done/finished
            // Sprint 10.51: localized sold/reserved markers for eBay's markets (DE/IT/FR/ES/NL), so a feed in
            // those languages drops a sold/reserved ad just like the HR/EN ones above. Accent-stripped to match.
            "verkauft",       // DE sold
            "reserviert",     // DE reserved
            "vendu",          // FR sold (vendu/vendue/vendus)
            "reserve",        // FR réservé (accent-stripped) / EN reserve(d)
            "venduto",        // IT sold (m.)
            "venduta",        // IT sold (f.)
            "vendido",        // ES sold (m.)
            "vendida",        // ES sold (f.)
            "verkocht",       // NL sold
            "gereserveerd"    // NL reserved
    );

    private MarketplaceListingFilter() {
    }

    /** True if any of the given texts (e.g. listing title, status label) marks the item as sold/closed. */
    public static boolean isSoldOrUnavailable(String... texts) {
        if (texts == null) return false;
        for (String text : texts) {
            String haystack = normalize(text);
            if (haystack.isEmpty()) continue;
            for (String marker : SOLD_MARKERS) {
                if (haystack.contains(marker)) return true;
            }
        }
        return false;
    }

    /**
     * True when a listing is too old to trust: missing/unparseable timestamp, or last checked more than
     * {@link #MARKETPLACE_STALE_AFTER_HOURS} hours before {@code now}. {@code lastCheckedAt} may be an ISO
     * instant, an offset date-time, or a plain {@code yyyy-MM-dd} date (treated as that day at 00:00 UTC).
     */
    public static boolean isStale(String lastCheckedAt, Instant now) {
        Instant checked = parseInstant(lastCheckedAt);
        if (checked == null || now == null) return true;
        return checked.isBefore(now.minus(Duration.ofHours(MARKETPLACE_STALE_AFTER_HOURS)));
    }

    /** The ingest test: drop a candidate listing if it is sold/unavailable or stale. */
    public static boolean shouldDrop(String title, String status, String lastCheckedAt, Instant now) {
        return isSoldOrUnavailable(title, status) || isStale(lastCheckedAt, now);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (RuntimeException ignored) {
            // not a full instant
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (RuntimeException ignored) {
            // not an offset date-time
        }
        try {
            return LocalDate.parse(trimmed.length() >= 10 ? trimmed.substring(0, 10) : trimmed)
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim();
    }
}
