// Sprint 10.190: the frontend deterministic detectors must survive whatever a real user pastes in — emoji, mixed
// scripts, stacked negation, code fragments, enormous input — without throwing. The exact answer for nonsense is
// not asserted (there is none), only that the functions stay safe and return a valid shape.
import { describe, expect, it } from 'vitest';
import { detectMultiRoom } from './multiRoom';
import { detectOutOfScope } from './outOfScope';
import { negatedRanges, isNegated, normalizeForMatch } from './negationScope';
import type { RoomType } from '../types';

const OOS_VALUES = new Set(['electronics', 'appliances', 'materials', 'outdoor', null]);
const ROOMS: RoomType[] = ['living-room', 'home-office', 'bedroom', 'home-gym', 'kitchen', 'dining-room', 'hallway', 'bathroom', 'studio'];

const CHAOS: unknown[] = [
  '', '   ', '\n\t\0', '😀🛋️🔥', '🏠 kuhinja 2000€ 🍳', 'ĐŽŠĆČ ćčžšđ', '日本語', 'مطبخ',
  'ne ne ne ne bez bez crne boje', 'NEĆU JEFTINO ALI JEFTINO', 'no no no no ikea',
  'kjøkken køkken kök keittiö cuisine cozinha', 'kuhinja spavaća kupaonica hodnik ured sve',
  '<script>alert(1)</script> kuhinja', 'SELECT * FROM x; DROP TABLE y;', '{"room":"kitchen"}',
  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'asdlkfj qwepoiru zxcvmnb', 'ne kuhinja da kuhinja ne kuhinja',
  'perilica bez perilice ali s perilicom ne bez klime', 'tv-bord tv-regal televizor 55 inch',
  'nicht ohne balkon', 'ne trebam perilicu, samo namjestaj', '‮ehcuk‬', 'k'.repeat(10000),
  'ne '.repeat(5000) + 'kuhinja i dnevni boravak', undefined, null,
];

function corpus(): unknown[] {
  const out = [...CHAOS];
  let s = 190190;
  const rand = () => (s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff; // deterministic LCG
  const seeds = CHAOS.filter((x): x is string => typeof x === 'string');
  for (let i = 0; i < 200; i++) {
    let piece = '';
    const parts = 1 + Math.floor(rand() * 4);
    for (let p = 0; p < parts; p++) {
      const chunk = seeds[Math.floor(rand() * seeds.length)];
      piece += (rand() < 0.5 ? chunk.toUpperCase() : chunk) + (rand() < 0.5 ? ', ' : ' i ');
    }
    out.push(piece);
  }
  return out;
}

describe('deterministic detectors never crash on unpredictable input', () => {
  it('detectMultiRoom stays safe and well-shaped', () => {
    for (const input of corpus()) {
      expect(() => {
        const hint = detectMultiRoom(input as string);
        if (hint !== null) {
          expect(Array.isArray(hint.rooms)).toBe(true);
          for (const r of hint.rooms) expect(ROOMS).toContain(r);
        }
      }).not.toThrow();
    }
  });

  it('detectOutOfScope stays safe and returns a valid category or null', () => {
    for (const input of corpus()) {
      expect(() => {
        const result = detectOutOfScope(input as string);
        expect(OOS_VALUES.has(result as string | null)).toBe(true);
      }).not.toThrow();
    }
  });

  it('negationScope helpers stay safe for any string and any offset', () => {
    for (const input of corpus()) {
      if (typeof input !== 'string') continue;
      expect(() => {
        const text = normalizeForMatch(input);
        const ranges = negatedRanges(text);
        for (const idx of [-100, -1, 0, 5, text.length, 1e9]) isNegated(ranges, idx);
      }).not.toThrow();
    }
  });
});
