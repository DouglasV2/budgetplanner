package ai.budgetspace.dto;

import java.util.List;

/**
 * Sprint 10.183 (Move-In QoL — adjust apartment): one room in an adjust request. {@code plan} is the room's
 * CURRENT chosen plan; {@code retained} true means "keep this room" (pass it through untouched); {@code
 * lockedProductIds} are the kept ("Zadrži ovaj proizvod") pieces that must not be swapped.
 */
public record AdjustRoomDto(String roomType, FurnishingPlanDto plan, boolean retained, List<String> lockedProductIds) {

    public AdjustRoomDto {
        lockedProductIds = lockedProductIds == null ? List.of() : lockedProductIds;
    }
}
