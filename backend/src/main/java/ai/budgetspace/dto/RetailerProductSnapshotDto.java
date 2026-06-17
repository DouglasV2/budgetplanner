package ai.budgetspace.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One product as it arrives from an external retailer snapshot.
 *
 * <p>This is the shape of a controlled, manually prepared retailer export. It is
 * deliberately <strong>not</strong> a live scraper: there is no browser
 * automation and no HTTP call to a retailer site. A snapshot is a JSON list of
 * these records that someone exports by hand and feeds into the import
 * pipeline.</p>
 *
 * <p>The snapshot is normalised into the existing {@link ImportProductDto} via
 * {@code RetailerCatalogAdapter} and then runs through the same validation,
 * taxonomy mapping and {@code externalId} de-duplication as the regular JSON/CSV
 * import from Sprint 8.3. That keeps a single source of truth for what is a
 * valid product.</p>
 */
public record RetailerProductSnapshotDto(
        String externalId,
        String name,
        String retailer,
        String category,
        BigDecimal price,
        String productUrl,
        String imageUrl,
        String availabilityStatus,
        String deliveryNote,
        String lastCheckedAt,
        List<String> roomTags,
        List<String> styleTags,
        String priceTier,
        String sourceType,
        String sourceName,
        String sourceReference,
        String dataQuality,
        String dataQualityNotes,
        // Sprint 10.7: optional colour/material tags a curated snapshot may declare for a product.
        List<String> colorTags,
        List<String> materialTags,
        // Sprint 10.13: optional reviews aggregate (#2) and market/country (#3).
        Integer reviewCount,
        String reviewsUrl,
        String market,
        // Sprint 10.13 (#2, go-wide): verified average star rating for display only (separate from the
        // planner's internal `rating`). Null = unknown.
        Double reviewRating,
        // Sprint 10.23 (road-to-production step 4): true when imageUrl was verified on the live product
        // page (og:image / main gallery image). Null/false = unverified → the UI keeps the placeholder.
        Boolean imageVerified
) {
    /**
     * Backwards-compatible constructor for snapshots prepared before Sprint 10.7. Colour/material,
     * reviews, market and image-verification default to empty; the import pipeline derives
     * colour/material from the name.
     */
    public RetailerProductSnapshotDto(
            String externalId, String name, String retailer, String category, BigDecimal price,
            String productUrl, String imageUrl, String availabilityStatus, String deliveryNote,
            String lastCheckedAt, List<String> roomTags, List<String> styleTags, String priceTier,
            String sourceType, String sourceName, String sourceReference, String dataQuality,
            String dataQualityNotes
    ) {
        this(externalId, name, retailer, category, price, productUrl, imageUrl, availabilityStatus,
                deliveryNote, lastCheckedAt, roomTags, styleTags, priceTier, sourceType, sourceName,
                sourceReference, dataQuality, dataQualityNotes, null, null, null, null, null, null, null);
    }
}
