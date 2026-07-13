package ai.budgetspace.dto;

import java.util.List;
import java.util.Map;

/**
 * Sprint 10.109 (Move-In / "Cijeli stan"): a whole-apartment request. {@code base} carries the shared
 * settings (style, stores, market, location, size — its budget/roomType are ignored); {@code rooms} is the
 * set of rooms to furnish; {@code totalBudget} is the one budget split across them.
 *
 * <p>Sprint 10.183 (Move-In QoL): {@code roomPriority} maps a roomType to how soon the user needs it
 * ({@code now} / {@code soon} / {@code later}). It is OPTIONAL and back-compatible — an old client that
 * omits it (or sends null) is normalized to an empty map, which the allocator reads as "all rooms equal
 * priority" (byte-identical to the pre-priority behaviour). It steers the budget split, not decoration.
 */
public record MoveInRequestDto(PlannerInputDto base, List<String> rooms, int totalBudget,
                               Map<String, String> roomPriority) {

    public MoveInRequestDto {
        rooms = rooms == null ? List.of() : rooms;
        roomPriority = roomPriority == null ? Map.of() : roomPriority;
    }

    /** Legacy 3-arg shape (pre-10.183): no room priorities. */
    public MoveInRequestDto(PlannerInputDto base, List<String> rooms, int totalBudget) {
        this(base, rooms, totalBudget, Map.of());
    }
}
