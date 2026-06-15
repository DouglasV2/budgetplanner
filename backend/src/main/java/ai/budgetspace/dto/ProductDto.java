package ai.budgetspace.dto;

import ai.budgetspace.product.Product;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public record ProductDto(
        String id,
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
        String externalId,
        String priceTier,
        String image,
        String url,
        double rating,
        boolean inStock,
        String note,
        List<String> colorTags,
        List<String> materialTags
) {
    public static ProductDto from(Product product) {
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getRetailer(),
                product.getCategory(),
                product.getPrice(),
                product.getOriginalPrice(),
                splitTags(product.getStyleTags()),
                splitTags(product.getRoomTags()),
                firstNonBlank(product.getImageUrl(), product.getImage()),
                firstNonBlank(product.getProductUrl(), product.getUrl()),
                firstNonBlank(product.getAvailabilityStatus(), product.isInStock() ? "in-stock" : "unavailable"),
                firstNonBlank(product.getDeliveryNote(), "Provjeri dostavu ili preuzimanje prije kupnje."),
                firstNonBlank(product.getLastCheckedAt(), "2026-06-12"),
                firstNonBlank(product.getExternalId(), product.getId()),
                firstNonBlank(product.getPriceTier(), inferPriceTier(product.getPrice())),
                product.getImage(),
                product.getUrl(),
                product.getRating(),
                product.isInStock(),
                product.getNote(),
                splitTags(product.getColorTags()),
                splitTags(product.getMaterialTags())
        );
    }

    private static List<String> splitTags(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        return fallback;
    }

    private static String inferPriceTier(BigDecimal price) {
        if (price == null) return "standard";
        if (price.compareTo(BigDecimal.valueOf(120)) <= 0) return "budget";
        if (price.compareTo(BigDecimal.valueOf(450)) <= 0) return "standard";
        return "premium";
    }
}
