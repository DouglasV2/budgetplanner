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
        String note
) {
}
