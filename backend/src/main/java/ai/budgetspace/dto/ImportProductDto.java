package ai.budgetspace.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A data transfer object used for importing new products into the system.
 *
 * <p>The current seed/mock implementation contains static product data. To
 * prepare for real imports we expose a dedicated DTO that mirrors the core
 * attributes of the {@link ai.budgetspace.product.Product} entity but
 * expresses list fields as {@link java.util.List} values for easier JSON
 * deserialisation. When imported these lists will be joined into comma
 * separated strings on the entity. Fields may be {@code null}; null
 * values indicate that existing values should be preserved or sensible
 * defaults applied.</p>
 */
public record ImportProductDto(
        String id,
        String externalId,
        String name,
        String retailer,
        String category,
        BigDecimal price,
        BigDecimal originalPrice,
        List<String> styleTags,
        List<String> roomTags,
        String imageUrl,
        String productUrl,
        String availabilityStatus,
        String deliveryNote,
        String lastCheckedAt,
        String priceTier,
        String note,
        String sourceType,
        String sourceName,
        String sourceReference,
        String dataQuality,
        String dataQualityNotes,
        // Sprint 10.7: optional colour/material tags joined onto the entity at import time.
        List<String> colorTags,
        List<String> materialTags,
        // Sprint 10.13: optional reviews aggregate (#2) and market/country (#3).
        Integer reviewCount,
        String reviewsUrl,
        String market,
        // Sprint 10.13 (#2, go-wide): verified average star rating, display only (separate from the
        // planner's internal `rating`). Null = unknown.
        Double reviewRating
) {
    /** Backwards-compatible constructor (pre-10.7): no colour/material, reviews or market. */
    public ImportProductDto(
            String id, String externalId, String name, String retailer, String category,
            BigDecimal price, BigDecimal originalPrice, List<String> styleTags, List<String> roomTags,
            String imageUrl, String productUrl, String availabilityStatus, String deliveryNote,
            String lastCheckedAt, String priceTier, String note, String sourceType, String sourceName,
            String sourceReference, String dataQuality, String dataQualityNotes
    ) {
        this(id, externalId, name, retailer, category, price, originalPrice, styleTags, roomTags,
                imageUrl, productUrl, availabilityStatus, deliveryNote, lastCheckedAt, priceTier, note,
                sourceType, sourceName, sourceReference, dataQuality, dataQualityNotes, null, null, null, null, null, null);
    }

    /** Backwards-compatible constructor (10.7-10.12): colour/material but no reviews or market. */
    public ImportProductDto(
            String id, String externalId, String name, String retailer, String category,
            BigDecimal price, BigDecimal originalPrice, List<String> styleTags, List<String> roomTags,
            String imageUrl, String productUrl, String availabilityStatus, String deliveryNote,
            String lastCheckedAt, String priceTier, String note, String sourceType, String sourceName,
            String sourceReference, String dataQuality, String dataQualityNotes,
            List<String> colorTags, List<String> materialTags
    ) {
        this(id, externalId, name, retailer, category, price, originalPrice, styleTags, roomTags,
                imageUrl, productUrl, availabilityStatus, deliveryNote, lastCheckedAt, priceTier, note,
                sourceType, sourceName, sourceReference, dataQuality, dataQualityNotes,
                colorTags, materialTags, null, null, null, null);
    }
}
