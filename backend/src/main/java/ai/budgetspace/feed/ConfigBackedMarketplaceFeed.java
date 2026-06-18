package ai.budgetspace.feed;

import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.product.CatalogSourcePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Sprint 10.49 — the default {@link MarketplaceFeed} placeholder for a second-hand marketplace. It is the
 * "Rabljeno" source slot a real official/partner/affiliate/API integration replaces later (e.g. an eBay
 * Browse API client, or a Njuškalo partner export).
 *
 * <p>It ships <strong>unconfigured</strong>: with no feed URL in the environment {@link #isConfigured()} is
 * {@code false} and {@link RetailerFeedImporter} skips it cleanly. Even if a URL is supplied, this default
 * imports nothing — there is no client wired yet — and says so rather than inventing listings. No
 * credentials are read or stored here.</p>
 */
public class ConfigBackedMarketplaceFeed implements MarketplaceFeed {
    private static final Logger log = LoggerFactory.getLogger(ConfigBackedMarketplaceFeed.class);

    private final String marketplace;
    private final String market;
    private final MarketplaceFeedProperties properties;

    public ConfigBackedMarketplaceFeed(String marketplace, String market, MarketplaceFeedProperties properties) {
        this.marketplace = marketplace;
        this.market = market;
        this.properties = properties;
    }

    @Override
    public String retailer() {
        return marketplace;
    }

    @Override
    public String market() {
        return market;
    }

    @Override
    public String sourceType() {
        return CatalogSourcePolicy.SOURCE_MARKETPLACE_LISTING;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured(marketplace);
    }

    @Override
    public String statusReason() {
        if (isConfigured()) {
            return "Marketplace feed URL je postavljen, ali klijent još nije implementiran — nema uvoza (placeholder).";
        }
        return "Marketplace placeholder (" + (market == null ? "multi" : market) + ") — nema feeda u okolini; "
                + "spremno za službeni/partnerski/affiliate feed. Nikad se ne scrape-a.";
    }

    /**
     * Returns no rows. A real marketplace client maps its compliant feed into verified second-hand rows
     * ({@code secondHand=true}, {@code sourceType=marketplace-listing}) and must run every candidate through
     * {@link ai.budgetspace.product.MarketplaceListingFilter} before returning — see {@link MarketplaceFeed}.
     */
    @Override
    public List<RetailerProductSnapshotDto> fetchSnapshot() {
        log.warn("Marketplace feed za {}: URL je konfiguriran, ali klijent nije implementiran — uvozim 0 redaka "
                + "(bez izmišljanja, bez scrape-a). Implementiraj MarketplaceFeed za {}.", marketplace, marketplace);
        return List.of();
    }
}
