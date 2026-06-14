package ai.budgetspace.product;

import ai.budgetspace.collector.CollectorRunStore;
import ai.budgetspace.collector.RetailerCollectorService;
import ai.budgetspace.dto.CatalogHealthDto;
import ai.budgetspace.dto.CollectorRequestDto;
import ai.budgetspace.dto.CollectorRunDetailDto;
import ai.budgetspace.dto.CollectorRunDto;
import ai.budgetspace.dto.CollectorRunSummaryDto;
import ai.budgetspace.dto.ImportProductDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.ProductDto;
import ai.budgetspace.dto.RetailerProductSnapshotDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class ProductController {
    private final ProductRepository productRepository;
    private final ProductImportService productImportService;
    private final RetailerSnapshotImportService retailerSnapshotImportService;
    private final RetailerCollectorService retailerCollectorService;
    private final CatalogHealthService catalogHealthService;
    private final CollectorRunStore collectorRunStore;

    public ProductController(ProductRepository productRepository, ProductImportService productImportService, RetailerSnapshotImportService retailerSnapshotImportService, RetailerCollectorService retailerCollectorService, CatalogHealthService catalogHealthService, CollectorRunStore collectorRunStore) {
        this.productRepository = productRepository;
        this.productImportService = productImportService;
        this.retailerSnapshotImportService = retailerSnapshotImportService;
        this.retailerCollectorService = retailerCollectorService;
        this.catalogHealthService = catalogHealthService;
        this.collectorRunStore = collectorRunStore;
    }

    @GetMapping("/api/products")
    public List<ProductDto> products(
            @RequestParam(required = false) String retailer,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        return productRepository.findAll().stream()
                .filter(product -> retailer == null || product.getRetailer().equalsIgnoreCase(retailer))
                .filter(product -> category == null || ProductTaxonomy.normalizeCategory(category)
                        .map(normalized -> product.getCategory().equalsIgnoreCase(normalized))
                        .orElse(product.getCategory().equalsIgnoreCase(category)))
                .filter(product -> roomType == null || hasTag(product.getRoomTags(), roomType))
                .filter(product -> maxPrice == null || product.getPrice().compareTo(maxPrice) <= 0)
                .map(ProductDto::from)
                .toList();
    }

    @PostMapping("/api/products/import")
    public ImportSummaryDto importProducts(@RequestBody List<ImportProductDto> imports) {
        return productImportService.importProducts(imports);
    }

    @PostMapping(value = "/api/products/import/csv", consumes = {"text/csv", "text/plain"})
    public ImportSummaryDto importCsv(@RequestBody String csv) {
        return productImportService.importCsv(csv);
    }

    @PostMapping("/api/products/import/ikea-starter")
    public ImportSummaryDto importIkeaStarterCatalog() {
        return productImportService.importIkeaStarterCatalog();
    }

    @PostMapping("/api/products/import/retailer-snapshot")
    public ImportSummaryDto importRetailerSnapshot(@RequestBody List<RetailerProductSnapshotDto> snapshot) {
        return retailerSnapshotImportService.importSnapshot(snapshot);
    }

    // Dev-only: collect a small, explicit list of product URLs into the catalog. No UI.
    @PostMapping("/api/products/collect/retailer-urls")
    public CollectorRunSummaryDto collectRetailerUrls(@RequestBody CollectorRequestDto request) {
        return retailerCollectorService.collect(request);
    }

    // Dev-only: is the catalog good enough for the planner? No UI.
    @GetMapping("/api/products/catalog-health")
    public CatalogHealthDto catalogHealth() {
        return catalogHealthService.compute();
    }

    // Dev-only: read past collector runs. No UI.
    @GetMapping("/api/products/collect/runs")
    public List<CollectorRunDto> collectorRuns() {
        return collectorRunStore.listRecent();
    }

    @GetMapping("/api/products/collect/runs/{runId}")
    public CollectorRunDetailDto collectorRunDetail(@PathVariable String runId) {
        return collectorRunStore.detail(runId).orElse(null);
    }

    private boolean hasTag(String csv, String value) {
        if (csv == null || value == null) return false;
        for (String tag : csv.split(",")) {
            if (tag.trim().equalsIgnoreCase(value.trim())) return true;
        }
        return false;
    }
}
