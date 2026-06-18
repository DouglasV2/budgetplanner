package ai.budgetspace.feed;

/**
 * Sprint 10.49 — a second-hand consumer marketplace feed (Njuškalo, eBay, Willhaben, Leboncoin, …).
 *
 * <p>It is the same pluggable contract as {@link RetailerFeed}, so {@link RetailerFeedImporter} consumes it
 * automatically — but its rows are <strong>used listings</strong>, which carries three non-negotiable rules
 * (see {@code docs/marketplace-sourcing.md}):</p>
 * <ul>
 *   <li>{@link #sourceType()} is {@code marketplace-listing} and every row is marked second-hand.</li>
 *   <li>Because a used listing disappears the moment it sells, {@link #fetchSnapshot()} MUST run every
 *       candidate through {@link ai.budgetspace.product.MarketplaceListingFilter} (drop PRODANO / reserved /
 *       expired / stale) <em>before</em> returning, so a sold ad is never imported.</li>
 *   <li><strong>Never scrape.</strong> Only an official API / partner / affiliate / data-export feed — the
 *       same rule as a 403 retailer. The default {@link ConfigBackedMarketplaceFeed} ships unconfigured.</li>
 * </ul>
 */
public interface MarketplaceFeed extends RetailerFeed {

    /** The market (2-letter country code) this marketplace primarily serves, or {@code null} if multi-market. */
    String market();
}
