// Sprint 10.185 (privacy/consent/legal hardening): practical guards that keep the legal pages, the analytics
// consent layer and the AI notice HONEST as the code evolves. These assert PROPERTIES (operator identity is
// present, forbidden claims stay gone, GA stays consent-gated, raw prompts never enter analytics) rather than
// whole paragraphs, so they are not brittle. Zero-dependency (regex + file reads) — run via `npm run check:legal`.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (rel) => readFileSync(join(frontend, rel), 'utf8');

const legal = read('src/legal.ts');
const i18n = read('src/i18n.ts');
const plannerForm = read('src/components/PlannerForm.tsx');
const main = read('src/main.tsx');
const analytics = read('src/utils/analytics.ts');
const planner = read('src/components/Planner.tsx');
const planResults = read('src/components/PlanResults.tsx');

const failures = [];
const ok = [];
function check(desc, condition) {
  if (condition) ok.push(desc);
  else failures.push(desc);
}

// Extract every trackEvent(...) call, tolerating one level of nested parentheses, across all analytics callers.
function trackEventCalls(source) {
  return source.match(/trackEvent\((?:[^()]|\([^()]*\))*\)/gs) ?? [];
}

// ── Impressum / operator identity (Part 2) ────────────────────────────────────────────────────────
check('Impressum shows the operator name (Bruno Pušić)', legal.includes('Bruno Pušić'));
check('Impressum shows the street address', legal.includes('Ulica Emanuela Vidovića 28'));
check('Impressum shows the postal city', legal.includes('10360 Sesvete'));
check('Operator shown for HR + EN + DE (Croatia/Croatia/Kroatien present)',
  legal.includes(', Hrvatska') && legal.includes(', Croatia') && legal.includes(', Kroatien'));
// No leftover "address will be added later" placeholder (the address must be present, not deferred).
check('No "address added later" placeholder remains',
  !/(address|adresa|Anschrift)[^.\n]{0,60}(will appear|added later|dodati|bit će|ergänzt|später)/i.test(legal));
// No fabricated OIB / VAT / registry NUMBER (the words may appear only to state that none exists).
check('No fabricated OIB number', !/OIB[^.\n]{0,20}\d{6,}/i.test(legal));
check('No fabricated VAT / USt-IdNr number', !/(VAT|USt-?IdNr|PDV)[^.\n]{0,20}[A-Z]{0,2}\d{6,}/i.test(legal));

// ── Privacy Policy accuracy (Part 3) ──────────────────────────────────────────────────────────────
check('Privacy Policy has a Google Analytics section', legal.includes('Google Analytics'));
check('Privacy Policy ties Analytics to consent (only after acceptance)',
  /(samo ako|only after you|only runs after|nur, nachdem|nur mit)/i.test(legal) && /Google Analytics/.test(legal));
// The forbidden claim must NOT come back (GA IS enabled, so "no tracking cookies" would be false).
check('Privacy Policy does NOT claim "no tracking cookies"',
  !/no tracking cookies/i.test(legal) && !/kolačiće za praćenje/i.test(legal) && !/Tracking-Cookies/i.test(legal));
check('Privacy Policy does NOT claim it never stores/handles IP',
  !/we do not store your ip/i.test(legal) && !/ne spremamo tvoju ip/i.test(legal) && !/speichern ihre ip-adresse nicht/i.test(legal));
check('Privacy Policy covers temporary IP processing for rate limiting/security',
  /IP/.test(legal) && /(privremeno|temporarily|vorübergehend)/i.test(legal) && /(ograničavanj|rate limit|Ratenbegrenz)/i.test(legal));
check('Privacy Policy discloses public shared-plan links',
  /(dijeljeni planovi|shared plans|geteilte Pläne)/i.test(legal) && /(svatko tko|anyone who|wer diesen)/i.test(legal));
check('Privacy Policy identifies the active processors (Google, Stripe)',
  legal.includes('Google') && legal.includes('Stripe'));
check('Privacy Policy mentions consent withdrawal via Postavke privatnosti / Privacy settings',
  /(Postavke privatnosti|Privacy settings|Datenschutz-Einstellungen)/i.test(legal));
check('Privacy Policy names the DPA (AZOP)', legal.includes('AZOP'));

// ── AI transparency (Part 4) ──────────────────────────────────────────────────────────────────────
check('AI notice key exists and is rendered near the planner input',
  i18n.includes("'planner.aiInteractionNotice'") && plannerForm.includes('planner.aiInteractionNotice'));
check('AI notice says AI *may* interpret (deterministic fallback exists — not "every plan is AI")',
  /(može protumačiti|may be interpreted|kann von KI)/i.test(i18n));
check('Privacy Policy AI section names the provider (Gemini) and the deterministic fallback',
  legal.includes('Gemini') && /(determinist|pravilima vođena|rule-based|regelbasiert)/i.test(legal));

// ── Analytics consent gating + no raw prompt in analytics (Part 1 / Part 4) ────────────────────────
check('GA is NOT started unconditionally in main.tsx', !/initAnalytics\s*\(/.test(main));
check('Consent layer gates analytics (ConsentProvider present)', main.includes('App') && read('src/App.tsx').includes('ConsentProvider'));
check('analytics.ts exposes the consent controls (init/enable/disable/configured)',
  /export function initAnalytics/.test(analytics) && /export function enableAnalytics/.test(analytics) &&
  /export function disableAnalytics/.test(analytics) && /export function analyticsConfigured/.test(analytics));
check('Advertising consent is never granted (ad_storage stays denied)',
  !/ad_storage['"]?\s*:\s*['"]granted/.test(analytics));

const allTrackCalls = [...trackEventCalls(planner), ...trackEventCalls(planResults)];
check('Found trackEvent calls to inspect', allTrackCalls.length > 0);
const leaky = allTrackCalls.filter((c) => /\bprompt\b/i.test(c));
check('No raw planner prompt is passed into any analytics event',
  leaky.length === 0);
if (leaky.length) console.error('  Leaky trackEvent call(s):\n   ' + leaky.join('\n   '));

// ── Report ─────────────────────────────────────────────────────────────────────────────────────────
if (failures.length === 0) {
  console.log(`check-legal: OK — ${ok.length} legal/analytics guards passed`);
  process.exit(0);
}
console.error(`\ncheck-legal: FAIL — ${failures.length} of ${ok.length + failures.length} guard(s) failed:`);
for (const f of failures) console.error(`  ✗ ${f}`);
process.exit(1);
