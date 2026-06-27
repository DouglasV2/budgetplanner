package ai.budgetspace.dto;

public record PlanItemDto(
        ProductDto product,
        String reason,
        String shoppingPriority,
        String shoppingRole,
        String stepTitle,
        // Sprint 10.120: how many of this product the plan includes (e.g. 6 dining chairs). Defaults to 1;
        // the line total is product.price * quantity.
        int quantity
) {
    /** Backwards-compatible constructor (pre-10.120): a single unit. */
    public PlanItemDto(ProductDto product, String reason, String shoppingPriority, String shoppingRole, String stepTitle) {
        this(product, reason, shoppingPriority, shoppingRole, stepTitle, 1);
    }
}
