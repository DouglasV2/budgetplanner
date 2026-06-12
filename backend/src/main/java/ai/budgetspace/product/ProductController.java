package ai.budgetspace.product;

import ai.budgetspace.dto.ImportProductDto;
import ai.budgetspace.dto.ImportSummaryDto;
import ai.budgetspace.dto.ProductDto;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ProductController(ProductRepository productRepository, ProductImportService productImportService) {
        this.productRepository = productRepository;
        this.productImportService = productImportService;
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
                .filter(product -> category == null || product.getCategory().equalsIgnoreCase(category))
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

    private boolean hasTag(String csv, String value) {
        if (csv == null || value == null) return false;
        for (String tag : csv.split(",")) {
            if (tag.trim().equalsIgnoreCase(value.trim())) return true;
        }
        return false;
    }
}
