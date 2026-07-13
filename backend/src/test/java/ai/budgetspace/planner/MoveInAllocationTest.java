package ai.budgetspace.planner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Sprint 10.109: the Move-In budget allocator is pure math (no catalog), so it is unit-tested directly.
class MoveInAllocationTest {

    @Test
    void feasibleSplitSumsToTotalAndCoversEveryFloor() {
        List<String> rooms = List.of("living-room", "bedroom", "hallway");
        double[] floors = {400, 300, 80};
        int[] alloc = PlannerService.allocateMoveIn(3000, rooms, floors, 780, false);

        assertEquals(3000, alloc[0] + alloc[1] + alloc[2], "per-room budgets must sum to the total exactly");
        assertTrue(alloc[0] >= 400, "living-room keeps at least its core floor");
        assertTrue(alloc[1] >= 300, "bedroom keeps at least its core floor");
        assertTrue(alloc[2] >= 80, "hallway keeps at least its core floor");
        assertTrue(alloc[0] > alloc[2], "the higher-weight room gets more of the leftover");
    }

    @Test
    void infeasibleBudgetStillSumsToTotalAndIsFloorProportional() {
        List<String> rooms = List.of("living-room", "bedroom");
        double[] floors = {2000, 1500};
        int[] alloc = PlannerService.allocateMoveIn(1000, rooms, floors, 3500, true);

        assertEquals(1000, alloc[0] + alloc[1], "even when the budget can't cover the floors, it is fully allocated");
        assertTrue(alloc[0] > alloc[1], "infeasible split is proportional to the (larger) living-room floor");
    }

    @Test
    void noFloorDataFallsBackToWeights() {
        List<String> rooms = List.of("living-room", "hallway");
        double[] floors = {0, 0};
        int[] alloc = PlannerService.allocateMoveIn(1000, rooms, floors, 0, false);

        assertEquals(1000, alloc[0] + alloc[1]);
        assertTrue(alloc[0] > alloc[1], "with no floor data, weight alone gives the living room more");
    }

    @Test
    void singleRoomGetsTheWholeBudget() {
        int[] alloc = PlannerService.allocateMoveIn(2500, List.of("bedroom"), new double[]{300}, 300, false);
        assertEquals(1, alloc.length);
        assertEquals(2500, alloc[0]);
    }

    // Sprint 10.158: capacity caps — a thin room must not strand budget it can never spend. Each cap is
    // ceil(max(capacity, floor) × 1.25): the headroom keeps the tiers (which plan with 0.82–0.98 of the
    // allocation) able to afford the pieces the capacity was measured from.

    @Test
    void thinRoomExcessMovesToTheRoomWithHeadroom() {
        List<String> rooms = List.of("living-room", "kitchen");
        int[] alloc = {2000, 1600};
        int[] out = PlannerService.capAllocationsToCapacity(alloc, rooms, new double[]{400, 100}, new double[]{10000, 300});

        assertEquals(375, out[1], "the kitchen is capped at its 300 capacity + tier headroom");
        assertEquals(3225, out[0], "the kitchen's stranded excess moves to the living room");
        assertEquals(3600, out[0] + out[1], "nothing is lost while a room still has headroom");
    }

    @Test
    void cappingCascadesWhenRedistributionOverflowsAnotherRoom() {
        List<String> rooms = List.of("living-room", "bedroom", "kitchen");
        int[] alloc = {1500, 1300, 1200};
        // Kitchen caps at 250 (200×1.25) → its 950 excess flows to living+bedroom; that pushes bedroom over
        // ITS 1500 cap (1200×1.25) → a second round moves bedroom's overflow to the living room too.
        int[] out = PlannerService.capAllocationsToCapacity(alloc, rooms, new double[]{400, 300, 100}, new double[]{10000, 1200, 200});

        assertEquals(250, out[2], "kitchen capped");
        assertEquals(1500, out[1], "bedroom capped after the first redistribution round");
        assertEquals(4000 - 250 - 1500, out[0], "the living room absorbs both overflows");
        assertEquals(4000, out[0] + out[1] + out[2]);
    }

    @Test
    void allRoomsCappedLeavesTheRestHonestlyUnallocated() {
        List<String> rooms = List.of("kitchen", "bathroom");
        int[] alloc = {2000, 1000};
        int[] out = PlannerService.capAllocationsToCapacity(alloc, rooms, new double[]{100, 80}, new double[]{300, 150});

        assertEquals(375, out[0]);
        assertEquals(188, out[1]);
        assertTrue(out[0] + out[1] < 3000, "when no room has headroom the leftover stays unallocated, not inflated");
    }

    @Test
    void capNeverDropsBelowTheCoreFloor() {
        // A capacity estimate below the core floor (odd catalog state) must not starve the room's essentials.
        int[] out = PlannerService.capAllocationsToCapacity(new int[]{500}, List.of("hallway"), new double[]{250}, new double[]{120});
        assertTrue(out[0] >= 250, "the core floor wins over a lower capacity estimate");
        assertTrue(out[0] < 500, "but the room is still capped below its naive share");
    }

    @Test
    void allocationsWithinCapacityAreUntouched() {
        List<String> rooms = List.of("living-room", "bedroom");
        int[] alloc = {2100, 1900};
        int[] out = PlannerService.capAllocationsToCapacity(alloc, rooms, new double[]{400, 300}, new double[]{5000, 4000});
        assertEquals(2100, out[0]);
        assertEquals(1900, out[1]);
    }

    // Sprint 10.183 (Move-In QoL): room priorities steer the split. now=1.5 / soon=1.0 / later=0.6.

    @Test
    void priorityShiftsLeftoverTowardTheNowRoom() {
        // Two equal-weight rooms (kitchen & dining-room, both weight 1.0), same floor — priority alone decides.
        List<String> rooms = List.of("kitchen", "dining-room");
        double[] floors = {200, 200};
        int[] alloc = PlannerService.allocateMoveIn(3000, rooms, floors, 400, false, new double[]{1.5, 0.6});

        assertEquals(3000, alloc[0] + alloc[1], "still sums to the total exactly");
        assertTrue(alloc[0] >= 200 && alloc[1] >= 200, "both rooms still keep their core floor when the budget allows");
        assertTrue(alloc[0] > alloc[1], "the 'now' kitchen gets more of the discretionary leftover than the 'later' dining room");
    }

    @Test
    void infeasibleBudgetReservesTheNowRoomsEssentialsFirst() {
        // Budget can't cover both floors (600 + 800 > 1000). The 'now' living room must fully fund its floor
        // first; the 'later' bedroom takes the shortfall and comes back under its floor (honest partial).
        List<String> rooms = List.of("living-room", "bedroom");
        double[] floors = {600, 800};
        int[] alloc = PlannerService.allocateMoveIn(1000, rooms, floors, 1400, true, new double[]{1.5, 0.6});

        assertEquals(1000, alloc[0] + alloc[1], "the whole budget is still allocated");
        assertEquals(600, alloc[0], "the 'now' living room keeps its full core floor");
        assertTrue(alloc[1] < 800, "the 'later' bedroom loses essentials to the higher priority, not the reverse");
    }

    @Test
    void noPriorityMatchesLegacyAllocation() {
        // All-1.0 factors must reproduce the pre-priority split exactly — feasible AND infeasible.
        List<String> feas = List.of("living-room", "bedroom", "hallway");
        assertArrayEquals(
                PlannerService.allocateMoveIn(3000, feas, new double[]{400, 300, 80}, 780, false),
                PlannerService.allocateMoveIn(3000, feas, new double[]{400, 300, 80}, 780, false, new double[]{1, 1, 1}));

        List<String> infeas = List.of("living-room", "bedroom");
        assertArrayEquals(
                PlannerService.allocateMoveIn(1000, infeas, new double[]{2000, 1500}, 3500, true),
                PlannerService.allocateMoveIn(1000, infeas, new double[]{2000, 1500}, 3500, true, new double[]{1, 1}));
    }
}
