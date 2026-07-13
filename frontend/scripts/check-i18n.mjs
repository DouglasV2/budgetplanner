// Sprint 10.183 (Move-In QoL): i18n completeness guard. The inline DICTIONARY in src/i18n.ts holds the hr/en
// source strings; every other market language lives in src/messages/<lang>.json and English is the runtime
// fallback. This guard FAILS the build when a DICTIONARY key is missing from any overlay (so a new string can't
// silently ship as English in a non-EN market), and WARNS about overlay keys no longer in the DICTIONARY.
// Zero-dependency (regex + JSON) — run via `npm run check:i18n`.
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const frontend = join(here, '..');

const i18nSrc = readFileSync(join(frontend, 'src', 'i18n.ts'), 'utf8');
// Only match keys inside the DICTIONARY (those that map to an object opening with `hr:`).
const keyRe = /^\s*'([^']+)'\s*:\s*\{\s*hr:/gm;
const dictKeys = new Set();
let match;
while ((match = keyRe.exec(i18nSrc)) !== null) dictKeys.add(match[1]);

if (dictKeys.size === 0) {
  console.error('check-i18n: could not extract any DICTIONARY keys from i18n.ts');
  process.exit(1);
}

// Pre-existing untranslated keys as of Sprint 10.183 — all from the DROPPED / unrendered subscription flow
// (Plus/Pro pricing, `plus.*` upsells) plus `header.menu`. They fall back to English and are out of this
// sprint's scope; excused here so the guard can still ENFORCE completeness for every new key going forward.
// Remove entries from this list as the owner translates them (or when the dead pricing keys are deleted).
const ALLOW_MISSING = new Set([
  'header.menu',
  'plus.aiUpsell', 'plus.saveLimitUpsell', 'plus.seePricing',
  'pricing.freeName', 'pricing.freeF4', 'pricing.plusName', 'pricing.plusPrice', 'pricing.plusTagline',
  'pricing.plusF1', 'pricing.plusF2', 'pricing.plusF3', 'pricing.plusF4', 'pricing.plusNote', 'pricing.plusActive',
  'pricing.waitlistEmail', 'pricing.waitlistCta', 'pricing.joined', 'pricing.upgradeCta', 'pricing.signInForPlus',
  'pricing.redirecting', 'pricing.checkoutError', 'pricing.welcome', 'pricing.proName', 'pricing.proPrice',
  'pricing.notifyCta', 'pricing.proNotified',
]);

const messagesDir = join(frontend, 'src', 'messages');
const overlays = readdirSync(messagesDir).filter((file) => file.endsWith('.json'));

let missingTotal = 0;
let orphanTotal = 0;
for (const file of overlays) {
  const data = JSON.parse(readFileSync(join(messagesDir, file), 'utf8'));
  const overlayKeys = new Set(Object.keys(data));
  const missing = [...dictKeys].filter((key) => !overlayKeys.has(key) && !ALLOW_MISSING.has(key));
  const orphan = [...overlayKeys].filter((key) => !dictKeys.has(key));
  if (missing.length) {
    missingTotal += missing.length;
    console.error(`\n${file}: MISSING ${missing.length} key(s):\n  ${missing.join('\n  ')}`);
  }
  if (orphan.length) {
    orphanTotal += orphan.length;
    console.warn(`\n${file}: ${orphan.length} orphan key(s) not in DICTIONARY (warning):\n  ${orphan.join('\n  ')}`);
  }
}

if (missingTotal === 0) {
  console.log(`check-i18n: OK — ${dictKeys.size} DICTIONARY keys present in all ${overlays.length} overlays` + (orphanTotal ? ` (${orphanTotal} orphan warnings)` : ''));
  process.exit(0);
}
console.error(`\ncheck-i18n: FAIL — ${missingTotal} missing key(s) across overlays.`);
process.exit(1);
