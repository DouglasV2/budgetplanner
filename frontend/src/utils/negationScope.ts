// Sprint 10.190: the frontend twin of NegationScope.java — same cues, same clause rules, same parity. It keeps
// the honest "we don't sell {X}" banner and the Move-In nudge from firing on something the user RULED OUT
// ("ne trebam perilicu, samo namještaj").
//
// Rules, in order:
//  1. The text is cut into clauses at , ; . ! ? or a CONTRAST conjunction ("ali/nego/but/aber/…"). A coordinating
//     "and" deliberately does NOT cut, so "ne zelim tamno i crno" negates both.
//  2. Within a clause the cues are counted. An ODD count negates from the end of the FIRST cue to the end of the
//     clause; an EVEN count cancels out ("nicht ohne …").
//  3. The span starts at the END of the first cue, so a token that CONTAINS the cue is never negated by it.

// "pas" is intentionally not a cue: French negation is discontinuous ("je NE veux PAS"), so counting both halves
// would cancel itself out — the "ne" carries it. This also keeps "pas cher" (cheap) clean.
const CUE_SOURCE =
  '\\bne\\b|\\bbez\\b|\\bnemoj\\w*|\\bnecu\\b|\\bnecemo\\b|\\bnikako\\b|\\bnije\\b|\\bnisam\\b' + // HR/BS/SR
  '|\\bbrez\\b|\\bnocem\\b' + // SI
  '|\\bnie\\b|\\bnechcem\\b' + // SK
  '|\\bnot\\b|\\bno\\b|\\bdont\\b|\\bdoesnt\\b|\\bwithout\\b|\\bnothing\\b|\\bnever\\b|\\bavoid\\b|\\bskip\\b' + // EN
  '|\\bnicht\\b|\\bkein\\w*|\\bohne\\b' + // DE
  '|\\bnon\\b|\\bsenza\\b|\\bniente\\b' + // IT
  '|\\bsin\\b|\\bnada\\b|\\bnunca\\b' + // ES
  '|\\bnao\\b|\\bsem\\b' + // PT
  '|\\bsans\\b|\\baucun\\w*|\\bjamais\\b' + // FR
  '|\\bniet\\b|\\bgeen\\b|\\bzonder\\b|\\bnooit\\b' + // NL
  '|\\bei\\b|\\bala\\b|\\bilman\\b' + // FI
  '|\\binte\\b|\\bingen\\b|\\binga\\b|\\butan\\b|\\baldrig\\b' + // SV
  '|\\bikke\\b|\\buten\\b|\\baldri\\b' + // NO
  '|\\buden\\b'; // DK

// The Portuguese "mas" (but) is deliberately absent — it collides with the far more common Spanish "mas" (more,
// as in "lo mas barato") and would end a Spanish negation early. Portuguese contrast still works via the comma.
const BOUNDARY_SOURCE =
  '[,;.!?]|\\bali\\b|\\bnego\\b|\\bvec\\b|\\bbut\\b|\\baber\\b|\\bsondern\\b|\\bma\\b|\\bpero\\b' +
  '|\\bsino\\b|\\bmais\\b|\\bmaar\\b|\\bmutta\\b|\\bmen\\b|\\bale\\b|\\bvendar\\b';

export type NegatedRange = [number, number];

/**
 * The shared normalization both detectors run before matching: lower-case, strip combining marks, and fold the
 * Nordic ø/æ that NFD leaves intact (å/ä/ö do decompose).
 */
export function normalizeForMatch(prompt: string): string {
  return prompt
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/ø/g, 'o')
    .replace(/æ/g, 'ae');
}

function clauses(text: string): NegatedRange[] {
  const out: NegatedRange[] = [];
  const boundary = new RegExp(BOUNDARY_SOURCE, 'g');
  let start = 0;
  let match: RegExpExecArray | null;
  while ((match = boundary.exec(text)) !== null) {
    if (match.index > start) out.push([start, match.index]);
    start = match.index + match[0].length;
    if (match[0].length === 0) boundary.lastIndex += 1; // never loop on a zero-length match
  }
  if (start < text.length) out.push([start, text.length]);
  return out;
}

/** The character ranges of `text` (already normalized) that sit inside a negated clause. */
export function negatedRanges(text: string): NegatedRange[] {
  if (!text) return [];
  const ranges: NegatedRange[] = [];
  for (const [clauseStart, clauseEnd] of clauses(text)) {
    const segment = text.slice(clauseStart, clauseEnd);
    const cue = new RegExp(CUE_SOURCE, 'g');
    let count = 0;
    let firstCueEnd = -1;
    let match: RegExpExecArray | null;
    while ((match = cue.exec(segment)) !== null) {
      count += 1;
      if (firstCueEnd < 0) firstCueEnd = clauseStart + match.index + match[0].length;
      if (match[0].length === 0) cue.lastIndex += 1;
    }
    if (count % 2 === 1) ranges.push([firstCueEnd, clauseEnd]);
  }
  return ranges;
}

/** True when a match STARTING at this index falls inside a negated clause. */
export function isNegated(ranges: NegatedRange[], index: number): boolean {
  return index >= 0 && ranges.some(([start, end]) => index >= start && index < end);
}

/**
 * Does `pattern` match `text` at least once OUTSIDE a negated clause? Replaces a plain `.test()` wherever a hit
 * should only count when the user actually asked for it.
 */
export function affirmativeHit(pattern: RegExp, text: string, ranges: NegatedRange[]): boolean {
  const flags = pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`;
  const scan = new RegExp(pattern.source, flags);
  let match: RegExpExecArray | null;
  while ((match = scan.exec(text)) !== null) {
    if (!isNegated(ranges, match.index)) return true;
    if (match[0].length === 0) scan.lastIndex += 1;
  }
  return false;
}
