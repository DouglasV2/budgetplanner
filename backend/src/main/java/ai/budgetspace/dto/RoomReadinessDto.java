package ai.budgetspace.dto;

import java.util.List;

/**
 * Whether the catalog can build a useful plan for one room (Sprint 9.6, dev tool).
 * {@code ready} is false when a required category has no usable product.
 */
public record RoomReadinessDto(
        String room,
        boolean ready,
        List<String> missingRequiredCategories,
        List<String> weakCategories,
        int usableProducts
) {
}
