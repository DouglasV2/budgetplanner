package ai.budgetspace.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sprint 10.109: the whole-apartment result. {@code grandTotal} sums each room's best-tier total.
 * {@code apartmentPartial} is true when the total can't even cover every room's cheapest core pieces;
 * {@code shortfall} is how much is missing in that case (0 otherwise) — the honest "budget too low" signal.
 *
 * <p>Sprint 10.183 (adjust apartment): {@code changed} is false when an adjust action found nothing useful to
 * do (so the UI can show an honest "already the smallest realistic number of stores" / "nothing to upgrade"
 * note instead of pretending), and {@code message} carries that hand-written note. A fresh generation always
 * uses the legacy 5-arg shape ({@code changed=true, message=null}).
 */
public record MoveInResponse(
        List<MoveInRoomDto> rooms,
        BigDecimal grandTotal,
        int totalBudget,
        boolean apartmentPartial,
        BigDecimal shortfall,
        boolean changed,
        String message
) {

    /** Legacy 5-arg shape (a fresh generation): always a change, no note. */
    public MoveInResponse(List<MoveInRoomDto> rooms, BigDecimal grandTotal, int totalBudget,
                          boolean apartmentPartial, BigDecimal shortfall) {
        this(rooms, grandTotal, totalBudget, apartmentPartial, shortfall, true, null);
    }
}
