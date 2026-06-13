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
        List<String> roomTags,
        List<String> styleTags,
        String priceTier
) {
}
