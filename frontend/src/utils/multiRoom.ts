import type { RoomType } from '../types';

// Sprint 10.116: detect when a free-text prompt describes MORE THAN ONE room (or a whole apartment). The
// single-room generator can only do one, so instead of silently building one room + underspending, we nudge
// the user to the "Cijeli stan" (Move-In) mode and pre-fill the rooms it found. Deterministic keyword check.

// NOTE: stems (dnevn, spavac, kuhinj, …) use a LEADING \b only — a trailing \b would block inflected forms
// ("spavacu", "dnevni", "kuhinju"). Short ambiguous words (bad, hal, kok, bano, buro) keep BOTH boundaries.
const ROOM_PATTERNS: Array<[RoomType, RegExp]> = [
  ['living-room', /\b(dnevn|living\s*room|salon|soggiorno|wohnzimmer|woonkamer|sala\s*de\s*estar)/],
  ['bedroom', /\b(spavac|bedroom|schlafzimmer|camera\s*da\s*letto|slaapkamer|dormitor|sovrum|soverom|makuuhuone|chambre)/],
  ['kitchen', /\b(kuhinj|kitchen|kuche|cucina|keuken|cocina|cozinha|keittio|kok\b)/],
  ['dining-room', /\b(blagovaon|dining|esszimmer|sala\s*da\s*pranzo|eetkamer|comedor|matsal)/],
  ['hallway', /\b(hodnik|predsobl|hallway|ingresso|entree|recibidor|flur\b|hal\b)/],
  ['bathroom', /\b(kupaon|bathroom|badezimmer|bagno|badkamer|kopalnic|bad\b|bano\b)/],
  ['home-office', /\b(radni\s*(kutak|stol|prostor)|home[\s-]*office|ufficio|oficina|kantoor|ured\b|buro\b)/]
];

const WHOLE_APARTMENT = /\b(cijeli\s*stan|citav\s*stan|cijeli\s*dom|cijelu?\s*kuc|sav\s*namjestaj|useljavam|prvi\s*stan|jednosoban|dvosoban|trosoban|cetverosoban|garsonijer|cijeli\s*prostor|whole\s*(apartment|flat|home|house)|entire\s*(apartment|flat|home)|first\s*(apartment|home)|ganze\s*wohnung)\b/;
const ROOM_COUNT = /\b[2-9]\s*(sob|room|zimmer|stanz|chambre)/; // "3 sobe", "3 rooms"

export interface MultiRoomHint {
  rooms: RoomType[];
}

export function detectMultiRoom(prompt: string | undefined | null): MultiRoomHint | null {
  if (!prompt) return null;
  const text = prompt.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');

  const rooms = ROOM_PATTERNS.filter(([, re]) => re.test(text)).map(([room]) => room);
  const wholeApartment = WHOLE_APARTMENT.test(text) || ROOM_COUNT.test(text);

  if (rooms.length >= 2) return { rooms };
  if (wholeApartment) return { rooms: rooms.length ? rooms : ['living-room', 'bedroom'] };
  return null;
}
