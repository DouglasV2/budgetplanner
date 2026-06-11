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
        String image,
        String url,
        double rating,
        boolean inStock,
        String note
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
                product.getImage(),
                product.getUrl(),
                product.getRating(),
                product.isInStock(),
                product.getNote()
        );
    }

    private static List<String> splitTags(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
