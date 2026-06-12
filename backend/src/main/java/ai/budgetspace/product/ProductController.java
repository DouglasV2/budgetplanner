package ai.budgetspace.product;

import ai.budgetspace.dto.ImportProductDto;
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

    /**
     * Import a list of products into the catalogue.
     *
     * <p>This endpoint allows the seed/mock catalogue to be extended with real product
     * data without writing a full scraper. Products are de-duplicated based on their
     * external identifier. If a matching product already exists, it will be updated
     * with the non-null fields from the provided record. Otherwise a new product is
     * created. The response returns the imported products as {@link ProductDto}s.</p>
     *
     * @param imports a list of products to import
     * @return the list of imported/updated products
     */
    @PostMapping("/api/products/import")
    public List<ProductDto> importProducts(@RequestBody List<ImportProductDto> imports) {
        List<ProductDto> result = new java.util.ArrayList<>();
        if (imports == null) return result;

        for (ImportProductDto dto : imports) {
            if (dto == null) continue;

            Product entity = null;
            if (dto.externalId() != null && !dto.externalId().isBlank()) {
                entity = productRepository.findByExternalId(dto.externalId()).orElse(null);
            }

            if (entity == null) {
                entity = new Product();
                if (dto.id() != null && !dto.id().isBlank()) {
                    entity.setId(dto.id());
                } else {
                    entity.setId(java.util.UUID.randomUUID().toString());
                }
                entity.setExternalId(dto.externalId());
                entity.setRating(0);
                entity.setInStock(true);
            }

            if (dto.externalId() != null) entity.setExternalId(dto.externalId());
            if (dto.name() != null) entity.setName(dto.name());
            if (dto.retailer() != null) entity.setRetailer(dto.retailer());
            if (dto.category() != null) entity.setCategory(mapCategory(dto.category()));
            if (dto.price() != null) entity.setPrice(dto.price());
            if (dto.originalPrice() != null) entity.setOriginalPrice(dto.originalPrice());
            if (dto.styleTags() != null) entity.setStyleTags(String.join(",", dto.styleTags()));
            if (dto.roomTags() != null) entity.setRoomTags(String.join(",", dto.roomTags()));
            if (dto.imageUrl() != null) {
                entity.setImageUrl(dto.imageUrl());
                entity.setImage(dto.imageUrl());
            }
            if (dto.productUrl() != null) {
                entity.setProductUrl(dto.productUrl());
                entity.setUrl(dto.productUrl());
            }
            if (dto.availabilityStatus() != null) entity.setAvailabilityStatus(dto.availabilityStatus());
            if (dto.deliveryNote() != null) entity.setDeliveryNote(dto.deliveryNote());
            if (dto.lastCheckedAt() != null) {
                entity.setLastCheckedAt(dto.lastCheckedAt());
            } else if (entity.getLastCheckedAt() == null || entity.getLastCheckedAt().isBlank()) {
                entity.setLastCheckedAt(java.time.LocalDate.now().toString());
            }
            if (dto.priceTier() != null) entity.setPriceTier(dto.priceTier());
            if (dto.note() != null) entity.setNote(dto.note());
            if (dto.availabilityStatus() != null) {
                entity.setInStock(!"unavailable".equalsIgnoreCase(dto.availabilityStatus()));
            }

            applyImportDefaults(entity);
            productRepository.save(entity);
            result.add(ProductDto.from(entity));
        }

        return result;
    }

    /**
     * Normalise category names across imports. Many retailers use slightly different
     * words for the same kind of item (e.g. "kauč", "sofa", "couch").
     * This helper collapses common synonyms into our canonical category names. If
     * no mapping is found the original value in lower case is returned.
     *
     * @param category the raw category from the import
     * @return a canonical category value
     */
    private String mapCategory(String category) {
        if (category == null) return null;
        String normalised = category.trim().toLowerCase();
        return switch (normalised) {
            case "kauč", "kauc", "sofa", "couch" -> "sofa";
            case "tv stand", "tv-stand", "tv unit", "tv-unit", "komoda" -> "tv-unit";
            case "stolić", "stol", "table", "coffee table" -> "table";
            case "tepih", "rug", "carpet" -> "rug";
            case "rasvjeta", "lighting", "lamp", "lampa" -> "lighting";
            case "dekor", "dekoracije", "decor", "decoration" -> "decor";
            case "pohrana", "storage", "orman", "ormar", "ladice" -> "storage";
            case "krevet", "bed" -> "bed";
            case "madrac", "mattress" -> "mattress";
            case "stolica", "chair" -> "chair";
            case "desk", "radni stol", "pisaći stol", "pisaci stol" -> "desk";
            case "gym equipment", "gym-equipment", "oprema", "sprava" -> "gym-equipment";
            default -> normalised;
        };
    }

    private void applyImportDefaults(Product entity) {
        if (entity.getStyleTags() == null) entity.setStyleTags("");
        if (entity.getRoomTags() == null) entity.setRoomTags("");
        if (entity.getImageUrl() == null) entity.setImageUrl(entity.getImage());
        if (entity.getProductUrl() == null) entity.setProductUrl(entity.getUrl());
        if (entity.getImage() == null) entity.setImage(entity.getImageUrl() == null ? "" : entity.getImageUrl());
        if (entity.getUrl() == null) entity.setUrl(entity.getProductUrl() == null ? "" : entity.getProductUrl());
        if (entity.getNote() == null) entity.setNote("");
    }

    private boolean hasTag(String csv, String value) {
        if (csv == null || value == null) return false;
        for (String tag : csv.split(",")) {
            if (tag.trim().equalsIgnoreCase(value.trim())) return true;
        }
        return false;
    }
}
