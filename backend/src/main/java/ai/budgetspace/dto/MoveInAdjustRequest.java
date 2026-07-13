package ai.budgetspace.dto;

import java.util.List;
import java.util.Map;

/**
 * Sprint 10.183 (Move-In QoL): adjust an existing whole-apartment plan without regenerating it. {@code base}
 * carries the shared settings (market/style/stores); {@code rooms} are the current per-room plans plus their
 * retained/locked state; {@code action} is one of {@code reduce-total} / {@code fewer-stores} /
 * {@code use-remaining}; {@code targetTotal} is the goal for {@code reduce-total} (ignored otherwise);
 * {@code roomPriority} steers which rooms use the remaining budget first.
 */
public record MoveInAdjustRequest(PlannerInputDto base, List<AdjustRoomDto> rooms, int totalBudget,
                                  String action, Integer targetTotal, Map<String, String> roomPriority) {

    public MoveInAdjustRequest {
        rooms = rooms == null ? List.of() : rooms;
        roomPriority = roomPriority == null ? Map.of() : roomPriority;
    }
}
