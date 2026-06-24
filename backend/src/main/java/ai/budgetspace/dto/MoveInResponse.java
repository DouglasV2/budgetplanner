package ai.budgetspace.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sprint 10.109: the whole-apartment result. {@code grandTotal} sums each room's best-tier total.
 * {@code apartmentPartial} is true when the total can't even cover every room's cheapest core pieces;
 * {@code shortfall} is how much is missing in that case (0 otherwise) — the honest "budget too low" signal.
 */
public record MoveInResponse(
        List<MoveInRoomDto> rooms,
        BigDecimal grandTotal,
        int totalBudget,
        boolean apartmentPartial,
        BigDecimal shortfall
) {
}
