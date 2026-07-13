// Sprint 10.183 (Move-In QoL): pure helpers for the whole-apartment ("Cijeli stan") planner — session
// (de)serialization, retained/keep math, apartment status, and the shopping checklist. Kept free of React
// so it can be unit-checked by a plain node script (scripts/check-move-in.mjs, transpiled via esbuild).
import type { RoomType, RoomPriority, FurnishingPlan, PlannerInput } from '../types';

// One furnished room in the apartment result. Each room owns its picked plan tier + the PlannerInput used to
// build it (so a per-room swap/adjust can reuse the single-room /replace + /adjust engine).
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
//  - market-specific (results/purchasedIds/lockedByRoom, all tied to market products) is dropped when the
//    active market differs, so a HR plan never leaks foreign-market products/prices into a DE session.
export interface MoveInSession {
  market: string;
  rooms: RoomType[];
  budget: number;
  priorities: Partial<Record<RoomType, RoomPriority>>;
  retainedRooms: RoomType[];
  results: RoomPlanResult[] | null;
  purchasedIds: string[];
  lockedByRoom: Partial<Record<RoomType, string[]>>;
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

export function emptySession(market: string): MoveInSession {
  return {
    market,
    rooms: [...MOVE_IN_DEFAULT_ROOMS],
    budget: MOVE_IN_DEFAULT_BUDGET,
    priorities: {},
    retainedRooms: [],
    results: null,
    purchasedIds: [],
    lockedByRoom: {},
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

function readLockedByRoom(value: unknown): Partial<Record<RoomType, string[]>> {
  const out: Partial<Record<RoomType, string[]>> = {};
  if (!isObject(value)) return out;
  for (const [room, ids] of Object.entries(value)) {
    const list = asStringArray(ids);
    if (list.length) out[room as RoomType] = list;
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
    base.lockedByRoom = readLockedByRoom(raw.lockedByRoom);
  }
  return base;
}

export function serializeSession(session: MoveInSession): string {
  return JSON.stringify(session);
}
