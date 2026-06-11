package ai.budgetspace.product;

import ai.budgetspace.dto.ProductDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class ProductController {
    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
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

    private boolean hasTag(String csv, String value) {
        if (csv == null || value == null) return false;
        for (String tag : csv.split(",")) {
            if (tag.trim().equalsIgnoreCase(value.trim())) return true;
        }
        return false;
    }
}
