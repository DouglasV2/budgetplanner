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
                null,
                snapshot.priceTier(),
                null
        );
    }
}
