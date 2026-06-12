package ai.budgetspace.dto;

public record PlanItemDto(
        ProductDto product,
        String reason,
        String shoppingPriority,
        String shoppingRole,
        String stepTitle
) {
}
