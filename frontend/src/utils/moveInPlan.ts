// Sprint 10.183 (Move-In QoL): pure helpers for the whole-apartment ("Cijeli stan") planner — session
// (de)serialization, retained/keep math, apartment status, and the shopping checklist. Kept free of React
// so it can be unit-checked by a plain node script (scripts/check-move-in.mjs, transpiled via esbuild).
import type { RoomType, RoomPriority, FurnishingPlan, PlannerInput } from '../types';

// One furnished room in the apartment result. Each room owns its picked plan tier + the PlannerInput used to
// build it (so a per-room swap/adjust can reuse the single-room /replace + /adjust engine). Retained ("kept")
// products live in input.lockedProductIds — the existing backend-honoured seam.
export interface RoomPlanResult {
  roomType: RoomType;
  labelKey: string;
  allocatedBudget: number;
  plan: FurnishingPlan;
  input: PlannerInput;
  hasItems: boolean;
  partial: boolean;
}

// The persisted whole-apartment session (localStorage). Split by market-sensitivity:
//  - market-agnostic (rooms/budget/priorities/retainedRooms) always restores;
//  - market-specific (results/purchasedIds, tied to a market's products) is dropped when the active market
//    differs, so a HR plan never leaks foreign-market products/prices into a DE session. Retained PRODUCTS ride
//    inside results[].input.lockedProductIds, so persisting results carries them too.
export interface MoveInSession {
  market: string;
  rooms: RoomType[];
  budget: number;
  priorities: Partial<Record<RoomType, RoomPriority>>;
  retainedRooms: RoomType[];
  results: RoomPlanResult[] | null;
  purchasedIds: string[];
}

export const MOVE_IN_DEFAULT_ROOMS: RoomType[] = ['living-room', 'bedroom'];
export const MOVE_IN_DEFAULT_BUDGET = 5000;
const PRIORITY_VALUES: readonly RoomPriority[] = ['now', 'soon', 'later'];

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === 'string') : [];
}

function qtyOf(item: { quantity?: number }): number {
  return item.quantity && item.quantity > 1 ? item.quantity : 1;
}

export function emptySession(market: string): MoveInSession {
  return {
    market,
    rooms: [...MOVE_IN_DEFAULT_ROOMS],
    budget: MOVE_IN_DEFAULT_BUDGET,
    priorities: {},
    retainedRooms: [],
    results: null,
    purchasedIds: [],
  };
}

function readPriorities(value: unknown): Partial<Record<RoomType, RoomPriority>> {
  const out: Partial<Record<RoomType, RoomPriority>> = {};
  if (!isObject(value)) return out;
  for (const [room, level] of Object.entries(value)) {
    if (typeof level === 'string' && (PRIORITY_VALUES as readonly string[]).includes(level)) {
      out[room as RoomType] = level as RoomPriority;
    }
  }
  return out;
}

// Rebuild a session from an unknown localStorage value. NEVER throws — any malformed field falls back to its
// default. Also migrates the old {rooms,budget}-only draft (no `market` field) transparently: with no market
// stamp the market-specific fields are simply absent, so only rooms+budget are carried forward.
export function hydrateSession(raw: unknown, market: string): MoveInSession {
  const base = emptySession(market);
  if (!isObject(raw)) return base;

  const rooms = asStringArray(raw.rooms) as RoomType[];
  if (rooms.length) base.rooms = rooms;
  if (typeof raw.budget === 'number' && raw.budget > 0) base.budget = Math.floor(raw.budget);
  base.priorities = readPriorities(raw.priorities);
  base.retainedRooms = asStringArray(raw.retainedRooms) as RoomType[];

  const sameMarket = typeof raw.market === 'string' && raw.market === market;
  if (sameMarket) {
    base.results = Array.isArray(raw.results) ? (raw.results as RoomPlanResult[]) : null;
    base.purchasedIds = asStringArray(raw.purchasedIds);
  }
  return base;
}

export function serializeSession(session: MoveInSession): string {
  return JSON.stringify(session);
}

// --- Retained ("keep") math -------------------------------------------------------------------------------

// The euro cost of everything the user asked to keep: a kept ROOM counts in full; in other rooms only the kept
// (locked) PRODUCTS count. Used to guard a newly-entered budget against the retained items.
export function retainedTotal(results: RoomPlanResult[] | null, retainedRooms: RoomType[]): number {
  if (!results) return 0;
  const keptRooms = new Set(retainedRooms);
  let sum = 0;
  for (const room of results) {
    if (keptRooms.has(room.roomType)) {
      sum += room.plan.total;
      continue;
    }
    const locked = new Set(room.input.lockedProductIds);
    for (const item of room.plan.items) {
      if (locked.has(item.product.id)) sum += item.product.price * qtyOf(item);
    }
  }
  return sum;
}

export function retainedExceedsBudget(results: RoomPlanResult[] | null, retainedRooms: RoomType[], budget: number): boolean {
  return retainedTotal(results, retainedRooms) > budget;
}

// On a market switch the kept PRODUCTS point at the old market's SKUs. Strip any locked id whose product is
// tagged with a different market, so we never carry a foreign-market product into the new market. Returns the
// cleaned results (a shallow copy of touched rooms) + how many locks were dropped.
export function clearForeignMarketRetained(
  results: RoomPlanResult[] | null,
  market: string,
): { results: RoomPlanResult[] | null; cleared: number } {
  if (!results) return { results, cleared: 0 };
  let cleared = 0;
  const next = results.map((room) => {
    const productById = new Map(room.plan.items.map((item) => [item.product.id, item.product] as const));
    const kept = room.input.lockedProductIds.filter((id) => {
      const product = productById.get(id);
      const foreign = !!product && !!product.market && product.market !== market;
      if (foreign) cleared += 1;
      return !foreign;
    });
    return kept.length === room.input.lockedProductIds.length
      ? room
      : { ...room, input: { ...room.input, lockedProductIds: kept } };
  });
  // Nothing foreign -> hand back the SAME array so callers can skip a state update.
  return { results: cleared === 0 ? results : next, cleared };
}
