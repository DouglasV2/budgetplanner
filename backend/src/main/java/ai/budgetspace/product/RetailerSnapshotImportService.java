package ai.budgetspace.product;

import ai.budgetspace.dto.ImportProductDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Imports a controlled retailer snapshot (see {@link RetailerProductSnapshotDto}).
 *
 * <p>This is the Sprint 8.4 step towards realer products. It does not scrape
 * anything: it takes an already-prepared snapshot, maps each item into the
 * internal {@link ImportProductDto} through {@link RetailerCatalogAdapter}, and
 * then reuses {@link ProductImportService} for validation, taxonomy mapping and
 * {@code externalId} de-duplication. The returned {@link ImportSummaryDto} is the
 * same summary shape as the regular JSON/CSV import.</p>
 */
@Service
public class RetailerSnapshotImportService {
    private final ProductImportService productImportService;
    private final RetailerCatalogAdapter retailerCatalogAdapter;

    public RetailerSnapshotImportService(ProductImportService productImportService, RetailerCatalogAdapter retailerCatalogAdapter) {
        this.productImportService = productImportService;
        this.retailerCatalogAdapter = retailerCatalogAdapter;
    }

    public ImportSummaryDto importSnapshot(List<RetailerProductSnapshotDto> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return productImportService.importProducts(List.of());
        }
        List<ImportProductDto> imports = snapshot.stream()
                .map(retailerCatalogAdapter::toImportProduct)
                .toList();
        return productImportService.importProducts(imports);
    }
}
