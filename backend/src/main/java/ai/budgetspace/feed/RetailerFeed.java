package ai.budgetspace.feed;

import ai.budgetspace.dto.RetailerProductSnapshotDto;

import java.util.List;

/**
 * Sprint 10.14 — the pluggable contract for an official / partner / affiliate product feed.
 *
 * <p>This is the seam a real feed integration drops into later for the feed-required retailers
 * (Decathlon, Pevex, Lesnina) — the ones whose sites return HTTP&nbsp;403 and which we therefore
 * never scrape (see {@code CatalogSourcePolicy}). The default implementation
 * ({@link ConfigBackedRetailerFeed}) is intentionally <strong>unconfigured</strong>: no credentials,
 * no tokens, no endpoints are shipped in the repo. A feed becomes active only when an operator
 * supplies its URL/credentials through environment variables at deploy time.</p>
 *
 * <p>Implementations must be honest: {@link #fetchSnapshot()} is only ever called when
 * {@link #isConfigured()} returns {@code true}, and it must return real, verified rows (or an empty
 * list) — never fabricated products, prices, images or URLs.</p>
 */
public interface RetailerFeed {

    /** The retailer this feed populates, e.g. {@code "Decathlon"}. */
    String retailer();

    /**
     * The provenance recorded on every imported product — one of
     * {@link ai.budgetspace.product.CatalogSourcePolicy#SOURCE_OFFICIAL_FEED} or
     * {@link ai.budgetspace.product.CatalogSourcePolicy#SOURCE_AFFILIATE_FEED}.
     */
    String sourceType();

    /** True only when real feed configuration (URL/credentials) has been supplied via the environment. */
    boolean isConfigured();

    /** A short, log-friendly explanation of why the feed is (not) configured. */
    String statusReason();

    /**
     * Fetches the current snapshot from the feed. Only invoked when {@link #isConfigured()} is
     * {@code true}. Returns the verified rows to import (possibly empty); must never fabricate data.
     */
    List<RetailerProductSnapshotDto> fetchSnapshot();
}
