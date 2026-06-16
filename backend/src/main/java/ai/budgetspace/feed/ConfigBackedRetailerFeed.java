package ai.budgetspace.feed;

import ai.budgetspace.dto.RetailerProductSnapshotDto;
import ai.budgetspace.product.CatalogSourcePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Sprint 10.14 — the default {@link RetailerFeed} for a feed-required retailer. It is the placeholder
 * slot a real official/partner feed integration replaces later.
 *
 * <p>Until that integration exists it ships <strong>unconfigured</strong>: with no feed URL in the
 * environment {@link #isConfigured()} is {@code false} and the importer cleanly skips it. If an
 * operator does supply a URL, this default still imports nothing — there is no parser/client wired
 * yet — and says so in the log rather than inventing products. No credentials are read or stored here.</p>
 */
public class ConfigBackedRetailerFeed implements RetailerFeed {
    private static final Logger log = LoggerFactory.getLogger(ConfigBackedRetailerFeed.class);

    private final String retailer;
    private final String sourceType;
    private final RetailerFeedProperties properties;

    public ConfigBackedRetailerFeed(String retailer, String sourceType, RetailerFeedProperties properties) {
        this.retailer = retailer;
        this.sourceType = sourceType;
        this.properties = properties;
    }

    @Override
    public String retailer() {
        return retailer;
    }

    @Override
    public String sourceType() {
        return sourceType;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured(retailer);
    }

    @Override
    public String statusReason() {
        if (isConfigured()) {
            return "Feed URL je postavljen, ali feed klijent još nije implementiran — nema uvoza (placeholder).";
        }
        return "Nije konfiguriran feed (nema URL-a u okolini). " + CatalogSourcePolicy.reasonFor(retailer);
    }

    /**
     * Returns no rows. A configured-but-unimplemented feed must not guess at products — it logs that a
     * real client is still required. A real integration replaces this class with one that fetches and
     * maps the official feed into verified {@link RetailerProductSnapshotDto} rows.
     */
    @Override
    public List<RetailerProductSnapshotDto> fetchSnapshot() {
        log.warn("Feed za {}: URL je konfiguriran, ali klijent nije implementiran — uvozim 0 redaka (bez izmišljanja). "
                + "Implementiraj RetailerFeed za {} da feed proradi.", retailer, retailer);
        return List.of();
    }
}
