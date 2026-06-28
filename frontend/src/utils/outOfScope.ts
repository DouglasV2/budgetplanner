// Sprint 10.115: honest out-of-scope detection. BudgetSpace plans FURNITURE. When a user asks for things we
// don't (and can't honestly) sell — electronics (a TV set, not a TV stand), white-goods appliances, or
// building materials — we say so plainly instead of silently handing back a furniture plan. This is a
// DETERMINISTIC keyword check (not an AI paragraph): it drives a fixed, localized banner.

export type OutOfScopeCategory = 'electronics' | 'appliances' | 'materials' | 'outdoor';

// A "tv komoda / tv stalak / tv element …" IS furniture (a TV stand) — never flag those as out of scope.
const TV_FURNITURE = /\btv[\s-]*(komod|stalak|stalk|element|ormaric|polic|klup|stol|stand|unit|bench|cabinet|board|meubel|mobile|m[oö]bel|board)/;

const ELECTRONICS = /\b(televizor|televizij|television|fernseher|televisore|smart\s*tv|monitor|racunal|laptop|kompjuter|computer|konzol|console|playstation|xbox|projektor|projector)\b/;
const TV_BARE = /\btv\b/;
const INCHES = /\b\d{2}\s*("|''|inc|incha|inch|zoll|pollic)/; // e.g. "55 inca", "55 inch"

// Sprint 10.124: broadened across the 15 markets' languages and fixed inflected stems (\bperilic\b never
// matched "perilica"). Text is accent-stripped (NFD) before matching, so patterns are written accent-free.
const APPLIANCES = /\b(klima|klimatizacij|klimaanlage|klimatsk\w*|air[\s-]*condition\w*|luftkondition\w*|aircond\w*|airco|aire\s*acondicion\w*|ar\s*condicionad\w*|climatiseur|climatisation|ilmastoint\w*|perilic\w*|ves[\s-]*masin\w*|washing\s*machine|lavadora|lavatrice|lave[\s-]*linge|machine\s*a\s*laver|tvattmaskin\w*|vaskemaskin\w*|pesukone|pralni\s*stroj|pracka|dryer|susilic\w*|wasmachine|waschmaschine|hladnjak|frizider|fridge|refrigerator|kuhlschrank|frigorifer\w*|frigorific\w*|frigo\b|nevera|koelkast|jaakaappi|hladilnik|chladnick\w*|kylskap|kjoleskap|koleskab|zamrzivac|freezer|gefrierschrank|congelador|stednjak|pecnic|stove|oven|cooker|herd|forno|cappa|napa|mikrovaln\w*|microwave|mikrowelle|microond\w*|dishwasher|geschirrspuler|lavastovigl\w*|lavavajilla\w*|lave[\s-]*vaisselle|diskmaskin\w*|opvaskemask\w*|astianpesukone|pomivalni\s*stroj|perilica\s*posud\w*|bojler|boiler|usisavac|vacuum|staubsauger|aspirapolvere|sushilo)\b/;

const MATERIALS = /\b(laminat|laminate|parket|parquet|plocic|keramik|tiles|fliesen|piastrell|carrelage|zbuka|plaster|verputz|gips|cement|beton|estrich|izolacij|insulation)\b/;

// Outdoor/garden furniture — the catalog has no outdoor range, so an indoor fabric sofa on a balcony is wrong.
const OUTDOOR = /\b(balkon|balcony|balkcongit|terasa|terase|teras\b|terrace|patio|vrtn\w*\s*namjest|vrtni|dvorist|outdoor|garden\s*furniture|gartenm[oö]bel|mobili\s*da\s*giardino|tuinmeubel|jardin)\b/;
const FLOORING = /\bpodov|\bza\s*pod\b|\bnovi\s*pod\b|\bpod\s*za\b|\bflooring\b|\bbodenbelag\b/; // flooring — NOT "podna lampa"
const WALL_PAINT = /\bboja\s*za\s*zid|\bzidn\w*\s*boj|\bwall\s*paint|\bwandfarbe\b/;

export function detectOutOfScope(prompt: string | undefined | null): OutOfScopeCategory | null {
  if (!prompt) return null;
  const text = prompt.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');

  if (ELECTRONICS.test(text) || INCHES.test(text) || (TV_BARE.test(text) && !TV_FURNITURE.test(text))) {
    return 'electronics';
  }
  if (APPLIANCES.test(text)) return 'appliances';
  if (MATERIALS.test(text) || FLOORING.test(text) || WALL_PAINT.test(text)) return 'materials';
  if (OUTDOOR.test(text)) return 'outdoor';
  return null;
}
