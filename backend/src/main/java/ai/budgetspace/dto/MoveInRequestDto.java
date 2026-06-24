package ai.budgetspace.dto;

import java.util.List;

/**
 * Sprint 10.109 (Move-In / "Cijeli stan"): a whole-apartment request. {@code base} carries the shared
 * settings (style, stores, market, location, size — its budget/roomType are ignored); {@code rooms} is the
 * set of rooms to furnish; {@code totalBudget} is the one budget split across them.
 */
public record MoveInRequestDto(PlannerInputDto base, List<String> rooms, int totalBudget) {
}
