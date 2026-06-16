package ai.budgetspace.product;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.21 — the second-hand marketplace availability guard (docs/marketplace-sourcing.md §4).
 * Proves a sold/reserved/expired listing is dropped on ingest so a {@code PRODANO} ad is never surfaced,
 * and that the short marketplace freshness window works.
 */
class MarketplaceListingFilterTest {

    private static final Instant NOW = Instant.parse("2026-06-16T12:00:00Z");

    @Test
    void detectsSoldOrReservedListingsCaseAndAccentInsensitive() {
        assertThat(MarketplaceListingFilter.isSoldOrUnavailable("Trosjed KIVIK - PRODANO")).isTrue();
        assertThat(MarketplaceListingFilter.isSoldOrUnavailable("kutna garnitura prodano!")).isTrue();
        assertThat(MarketplaceListingFilter.isSoldOrUnavailable("Stol", "Rezervirano")).isTrue();
        assertThat(MarketplaceListingFilter.isSoldOrUnavailable("SOLD OUT")).isTrue();
        assertThat(MarketplaceListingFilter.isSoldOrUnavailable("Oglas završeno")).isTrue();   // accent
        assertThat(MarketplaceListingFilter.isSoldOrUnavailable("Povučeno iz prodaje")).isTrue(); // accent
        assertThat(MarketplaceListingFilter.isSoldOrUnavailable("Trenutno nije dostupno")).isTrue();
    }

    @Test
    void keepsCleanActiveListings() {
        assertThat(MarketplaceListingFilter.isSoldOrUnavailable("Lijepa fotelja, malo korištena, Zagreb")).isFalse();
        assertThat(MarketplaceListingFilter.isSoldOrUnavailable("MALM komoda 3 ladice, bijela")).isFalse();
        assertThat(MarketplaceListingFilter.isSoldOrUnavailable(null, "")).isFalse();
    }

    @Test
    void marketplaceFreshnessWindowIsHours() {
        // 12h old → fresh (within the 24h window).
        assertThat(MarketplaceListingFilter.isStale("2026-06-16T00:00:00Z", NOW)).isFalse();
        // exactly now → fresh.
        assertThat(MarketplaceListingFilter.isStale("2026-06-16T12:00:00Z", NOW)).isFalse();
        // 2 days old → stale.
        assertThat(MarketplaceListingFilter.isStale("2026-06-14", NOW)).isTrue();
        // missing / unparseable → stale (never trust an undated listing).
        assertThat(MarketplaceListingFilter.isStale(null, NOW)).isTrue();
        assertThat(MarketplaceListingFilter.isStale("nekad", NOW)).isTrue();
    }

    @Test
    void shouldDropCombinesSoldAndStale() {
        // Sold but fresh → drop.
        assertThat(MarketplaceListingFilter.shouldDrop("KIVIK PRODANO", null, "2026-06-16T12:00:00Z", NOW)).isTrue();
        // Active but stale → drop.
        assertThat(MarketplaceListingFilter.shouldDrop("KIVIK trosjed", "aktivno", "2026-06-10", NOW)).isTrue();
        // Active and fresh → keep.
        assertThat(MarketplaceListingFilter.shouldDrop("KIVIK trosjed, Split", "aktivno", "2026-06-16T06:00:00Z", NOW)).isFalse();
    }
}
