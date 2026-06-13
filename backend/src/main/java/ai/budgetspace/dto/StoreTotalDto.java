package ai.budgetspace.dto;

import java.math.BigDecimal;

/**
 * How much of a plan is bought in one store, and how many items that is.
 * Used by the shopping list so a person sees the cost per store at a glance.
 */
public record StoreTotalDto(
        String retailer,
        BigDecimal total,
        int itemCount
) {
}
