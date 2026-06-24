package ai.budgetspace.planner;

import org.junit.jupiter.api.Test;

import java.util.List;

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
}
