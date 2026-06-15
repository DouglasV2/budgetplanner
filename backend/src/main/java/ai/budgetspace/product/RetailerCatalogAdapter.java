package ai.budgetspace.product;

import ai.budgetspace.dto.ImportProductDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import org.springframework.stereotype.Component;

/**
 * Reshapes an external {@link RetailerProductSnapshotDto} into the internal
 * {@link ImportProductDto} used by the import pipeline.
 *
 * <p>The adapter only re-maps fields. It does no validation and no taxonomy
 * normalisation on purpose — that stays inside {@link ProductImportService} so
 * snapshot products go through exactly the same checks as a regular JSON/CSV
 * import. This is the single place that knows the snapshot shape, so adding a
 * second real retailer later means changing only this mapping, not the planner
 * or the validation rules.</p>
 */
@Component
public class RetailerCatalogAdapter {

    public ImportProductDto toImportProduct(RetailerProductSnapshotDto snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new ImportProductDto(
                null,
                snapshot.externalId(),
                snapshot.name(),
                snapshot.retailer(),
                snapshot.category(),
                snapshot.price(),
                null,
                snapshot.styleTags(),
                snapshot.roomTags(),
                snapshot.imageUrl(),
                snapshot.productUrl(),
                snapshot.availabilityStatus(),
                snapshot.deliveryNote(),
                snapshot.lastCheckedAt(),
                snapshot.priceTier(),
                null,
                defaultSourceType(snapshot.sourceType()),
                defaultSourceName(snapshot.sourceName(), snapshot.retailer()),
                snapshot.sourceReference(),
                snapshot.dataQuality(),
                snapshot.dataQualityNotes(),
                snapshot.colorTags(),
                snapshot.materialTags(),
                snapshot.reviewCount(),
                snapshot.reviewsUrl(),
                snapshot.market()
        );
    }

    // A snapshot that does not say where it came from is, by definition, a retailer snapshot.
    private String defaultSourceType(String sourceType) {
        return sourceType == null || sourceType.isBlank() ? "retailer-snapshot" : sourceType;
    }

    // Source name defaults to the retailer so a snapshot always has a sensible source.
    private String defaultSourceName(String sourceName, String retailer) {
        return sourceName == null || sourceName.isBlank() ? retailer : sourceName;
    }
}
