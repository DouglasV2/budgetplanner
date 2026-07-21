// Sprint 10.191: acceptance guard for the impersonal copy refit. FAILS while any product-UI string still carries
// a stylistic em-dash "—", or when an overlay value has lost/gained a {placeholder} vs the English source.
// Zero-dependency. Run via `npm run check:copy`. legal.ts is intentionally NOT covered (formal legal prose).
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const frontend = join(here, '..');
const EM_DASH = '—';
const placeholders = (s) => (s.match(/\{[^}]+\}/g) ?? []).sort().join(',');

let failures = 0;
const fail = (msg) => { console.error(msg); failures++; };

// --- i18n.ts: strip full-line comments, then no em-dash may remain in the source strings ---
const i18nRaw = readFileSync(join(frontend, 'src', 'i18n.ts'), 'utf8');
const i18nNoComments = i18nRaw.replace(/^\s*\/\/.*$/gm, '');
if (i18nNoComments.includes(EM_DASH)) {
  const n = (i18nNoComments.match(/—/g) ?? []).length;
  fail(`i18n.ts: ${n} em-dash(es) remain in copy`);
}

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
    if (value.includes(EM_DASH)) fail(`${file} [${key}]: em-dash remains`);
    if (key in enByKey && placeholders(value) !== placeholders(enByKey[key])) {
      fail(`${file} [${key}]: placeholders "${placeholders(value)}" != en "${placeholders(enByKey[key])}"`);
    }
  }
}

if (failures === 0) { console.log('check-copy: OK — no em-dash, placeholders intact'); process.exit(0); }
console.error(`\ncheck-copy: FAIL — ${failures} issue(s).`);
process.exit(1);
