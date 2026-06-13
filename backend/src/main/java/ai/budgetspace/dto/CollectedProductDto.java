package ai.budgetspace.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One product as the collector managed to read it from a product page. It mirrors the
 * {@link RetailerProductSnapshotDto} fields so it can flow into the existing import
 * pipeline (validation + {@code externalId} de-duplication) without a new save path.
 *
 * <p>Fields may be {@code null} when the page did not expose them; the collector service
 * decides whether the result is good enough to import (a missing price, for example, is
 * skipped with a clear error).</p>
 */
public record CollectedProductDto(
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
        String dataQualityNotes
) {
    public RetailerProductSnapshotDto toSnapshot() {
        return new RetailerProductSnapshotDto(
                externalId, name, retailer, category, price, productUrl, imageUrl,
                availabilityStatus, deliveryNote, lastCheckedAt, roomTags, styleTags, priceTier,
                sourceType, sourceName, sourceReference, dataQuality, dataQualityNotes
        );
    }
}
