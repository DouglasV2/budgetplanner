// Sprint 10.191: acceptance guard for the impersonal copy refit. FAILS while any product-UI string still carries
// a stylistic dash, or when an overlay value has lost/gained a {placeholder} vs the English source.
// Zero-dependency. Run via `npm run check:copy`.
//
// Sprint 10.193 widened it twice, after both gaps shipped:
//   * legal.ts IS now covered. It was excluded as "formal legal prose", but 299 em-dashes then sat in the
//     documents every user can open, which is the loudest machine-written tell in the whole product.
//   * the EN dash "–" counts too. The guard only looked for "—", so the Swedish overlay and the whole Swedish
//     legal document kept theirs (Swedish sets a tankstreck as an en dash) and passed green.
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const frontend = join(here, '..');
const DASHES = /[—–]/g;
const placeholders = (s) => (s.match(/\{[^}]+\}/g) ?? []).sort().join(',');

let failures = 0;
const fail = (msg) => { console.error(msg); failures++; };

// --- i18n.ts + legal.ts: strip full-line comments, then no dash may remain in the source strings ---
for (const file of ['i18n.ts', 'legal.ts']) {
  const noComments = readFileSync(join(frontend, 'src', file), 'utf8').replace(/^\s*\/\/.*$/gm, '');
  const n = (noComments.match(DASHES) ?? []).length;
  if (n) fail(`${file}: ${n} stylistic dash(es) remain in copy`);
}
const i18nRaw = readFileSync(join(frontend, 'src', 'i18n.ts'), 'utf8');
const i18nNoComments = i18nRaw.replace(/^\s*\/\/.*$/gm, '');

// English source placeholders per key (comments stripped; tolerant of multi-line entries and escaped quotes).
const enByKey = {};
const re = /'([^']+)'\s*:\s*\{\s*hr:\s*'(?:[^'\\]|\\.)*'\s*,\s*en:\s*'((?:[^'\\]|\\.)*)'\s*\}/gs;
let m;
while ((m = re.exec(i18nNoComments)) !== null) enByKey[m[1]] = m[2];

// --- overlays: no em-dash in any value; placeholders must match the English source for that key ---
const dir = join(frontend, 'src', 'messages');
for (const file of readdirSync(dir).filter((f) => f.endsWith('.json'))) {
  const data = JSON.parse(readFileSync(join(dir, file), 'utf8'));
  for (const [key, value] of Object.entries(data)) {
    if (typeof value !== 'string') continue;
    if (DASHES.test(value)) fail(`${file} [${key}]: stylistic dash remains`);
    DASHES.lastIndex = 0; // the regex is /g, so a stale lastIndex would skip the next value
    if (key in enByKey && placeholders(value) !== placeholders(enByKey[key])) {
      fail(`${file} [${key}]: placeholders "${placeholders(value)}" != en "${placeholders(enByKey[key])}"`);
    }
  }
}

if (failures === 0) { console.log('check-copy: OK, no stylistic dash, placeholders intact'); process.exit(0); }
console.error(`\ncheck-copy: FAIL, ${failures} issue(s).`);
process.exit(1);
