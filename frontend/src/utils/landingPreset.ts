import type { RoomType } from '../types';

// SEO landing pages (e.g. /hr/opremanje-prvog-stana/) link into the planner with a preset in the query string,
// for example  /?mode=single&room=living-room&budget=1000#planner. This helper parses AND validates those
// params so the planner can open pre-filled, without the static landing pages ever touching planner internals.
// Everything here is defensive: an unknown mode, a bad room, or a junk budget is ignored — it never throws,
// never yields NaN, and never invents selected rooms.

export type LandingScope = 'single' | 'apartment';

export interface LandingPreset {
  scope: LandingScope;
  /** Single mode: the explicitly chosen room (so the planner marks roomType as NOT inferred). */
  room?: RoomType;
  /** Move-in mode: the pre-selected rooms (validated + de-duped; omitted when none are valid). */
  rooms?: RoomType[];
  /** A sane, positive whole budget in the market currency; omitted when missing or out of range. */
  budget?: number;
}

// A furnishing budget above this isn't realistic — treat it as junk (e.g. an overflow attempt) and ignore it
// rather than clamp to a misleading figure. Deliberately well under the in-app hard cap (10M) that guards the
// separate int-overflow→400 failure; this is about not seeding the form with an absurd number from a URL.
const MAX_BUDGET = 1_000_000;

// The single source of truth for validation is the project's RoomType union (keep in sync with src/types).
const ROOM_TYPES: readonly RoomType[] = [
  'living-room', 'home-office', 'bedroom', 'home-gym', 'kitchen', 'dining-room', 'hallway', 'bathroom', 'studio',
];

function isRoomType(value: string): value is RoomType {
  return (ROOM_TYPES as readonly string[]).includes(value);
}

function parseBudget(raw: string | null): number | undefined {
  if (raw === null) return undefined;
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  const value = Number(trimmed);
  // Number('12px') is NaN and Number(' ') is 0 — require a finite, positive, in-range whole number.
  if (!Number.isFinite(value)) return undefined;
  const whole = Math.floor(value);
  if (whole <= 0 || whole > MAX_BUDGET) return undefined;
  return whole;
}

function parseRoom(raw: string | null): RoomType | undefined {
  if (raw === null) return undefined;
  const value = raw.trim();
  return isRoomType(value) ? value : undefined;
}

function parseRooms(raw: string | null): RoomType[] | undefined {
  if (raw === null) return undefined;
  const rooms: RoomType[] = [];
  for (const part of raw.split(',')) {
    const value = part.trim();
    if (isRoomType(value) && !rooms.includes(value)) rooms.push(value);
  }
  // No valid rooms → undefined, so a move-in preset opens the apartment scope WITHOUT inventing a selection.
  return rooms.length ? rooms : undefined;
}

// A shared-plan link (/plan/<id>) must be completely unaffected by landing-preset logic — opening a shared plan
// keeps working exactly as before. Mirrors isSharedPlanLink() in App.tsx and readSharedPlanIdFromUrl() in Planner.
function isSharedPlanPath(pathname: string): boolean {
  return /^\/plan\/[^/]+$/.test(pathname);
}

/**
 * Pure parser for a landing preset — exported so it can be unit-tested without a DOM.
 * Returns null when there's no actionable preset (unknown/missing mode, or a shared-plan path), which keeps the
 * app's existing behaviour untouched.
 */
export function parseLandingPreset(search: string, pathname: string): LandingPreset | null {
  if (isSharedPlanPath(pathname)) return null;

  const params = new URLSearchParams(search);
  const budget = parseBudget(params.get('budget'));

  switch (params.get('mode')) {
    case 'single':
      return { scope: 'single', room: parseRoom(params.get('room')), budget };
    case 'move-in':
      return { scope: 'apartment', rooms: parseRooms(params.get('rooms')), budget };
    default:
      return null;
  }
}

/** Reads the preset from the current URL. Client-only; returns null in a non-browser context. */
export function readLandingPreset(): LandingPreset | null {
  if (typeof window === 'undefined') return null;
  return parseLandingPreset(window.location.search, window.location.pathname);
}
