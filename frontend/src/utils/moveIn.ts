// Sprint 10.109: Move-In ("Cijeli stan") — budget allocation across multiple rooms.
//
// This is a thin orchestration layer ON TOP OF the existing single-room planner: we split one total
// budget across the chosen rooms, then call the normal generate-fast/generate per room. Nothing about
// the single-room flow changes — apartment mode is a separate, opt-in path.
//
// Phase 1 (frontend, no backend change): a deterministic WEIGHTED split. Each room gets a share of the
// total proportional to a static "typical furnishing cost" weight (a living room needs more than a
// hallway). The split is exact — the per-room budgets always sum back to the total (leftover cents go
// to the rooms with the largest fractional share). Phase 2 (later, backend) will make the split
// catalog-floor-aware so a room can never be starved below its core minimum.

import type { RoomType } from '../types';

// Relative "how much furnishing this room typically needs" weights. Tunable; not currency-specific
// (they only set PROPORTIONS, so they work in EUR/GBP/NOK/… alike).
export const MOVE_IN_ROOM_WEIGHTS: Record<RoomType, number> = {
  'living-room': 1.4,
  bedroom: 1.2,
  kitchen: 1.0,
  'dining-room': 1.0,
  'home-office': 0.9,
  'home-gym': 0.9,
  hallway: 0.5,
  bathroom: 0.5,
};

export interface RoomAllocation {
  roomType: RoomType;
  budget: number;
}

// Split `total` across `rooms` by weight. Guarantees: every returned budget is a non-negative integer
// and the budgets sum EXACTLY to `total` (so the user's whole budget is accounted for). Order of the
// returned array matches the input `rooms` order.
export function allocateBudget(total: number, rooms: RoomType[]): RoomAllocation[] {
  if (rooms.length === 0) return [];
  const safeTotal = Math.max(0, Math.floor(total));

  const weights = rooms.map((room) => MOVE_IN_ROOM_WEIGHTS[room] ?? 1.0);
  const weightSum = weights.reduce((sum, weight) => sum + weight, 0) || rooms.length;

  // Ideal (fractional) share per room, then floor and hand out the remainder to the biggest fractions
  // so the integers add up to exactly `total`.
  const ideal = weights.map((weight) => (safeTotal * weight) / weightSum);
  const budgets = ideal.map((value) => Math.floor(value));
  let remainder = safeTotal - budgets.reduce((sum, value) => sum + value, 0);

  const byFraction = ideal
    .map((value, index) => ({ index, fraction: value - Math.floor(value) }))
    .sort((a, b) => b.fraction - a.fraction);

  for (let i = 0; remainder > 0 && byFraction.length > 0; i++, remainder--) {
    budgets[byFraction[i % byFraction.length].index] += 1;
  }

  return rooms.map((roomType, index) => ({ roomType, budget: budgets[index] }));
}

// Sum the chosen-per-room plan totals into one apartment grand total (rounded to whole currency units).
export function grandTotal(roomTotals: number[]): number {
  return Math.round(roomTotals.reduce((sum, value) => sum + value, 0));
}
