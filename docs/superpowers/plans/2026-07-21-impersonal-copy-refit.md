# Impersonal Copy Refit + Em-dash Removal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite all product UI copy in 15 languages into an impersonal/neutral voice (no "ti"/"you", controls become noun labels) and remove every stylistic em-dash "—".

**Architecture:** Copy-only edits to 13 data files: `frontend/src/i18n.ts` (the `hr`/`en` dictionary) and the 12 `frontend/src/messages/*.json` overlays. A new guard script encodes the acceptance criteria (no em-dash + placeholder integrity). HR/EN are rewritten by hand as the reference voice; the 12 overlays are rewritten by a Workflow, one agent per language, each editing its own file. No code/logic/component/backend changes.

**Tech Stack:** TypeScript/React frontend; Node (zero-dep) check scripts; vitest; the Workflow tool.

**Spec:** `docs/superpowers/specs/2026-07-21-impersonal-copy-refit-design.md`

## Global Constraints

- Copy is centralised: `src/i18n.ts` holds inline `hr` + `en` per key and defines the key set; `src/messages/<lang>.json` overlays hold one value per key for de/it/fi/fr/nl/sk/es/pt/no/sv/da/sl.
- **No new or removed keys.** `node frontend/scripts/check-i18n.mjs` is the hard parity gate.
- **Preserve every `{placeholder}`** (`{amount}`, `{room}`, `{count}`, `{store}`, …) exactly.
- **Voice:** impersonal/neutral — no ti/vi/du/Sie/tu/vous/you and no user-possessives; controls → concise noun labels; descriptions → impersonal statements. Must read as natural hand-written copy, NOT stiff machine text (standing owner rule: no AI-vibecoded copy).
- **English control labels stay natural** — `Save plan` stays; do not force `Saving plan`.
- **Em-dash "—" (U+2014) only** is the target; en-dash "–" and hyphen "-" in ranges/compounds stay.
- **Out of scope:** `src/legal.ts` (formal legal prose); backend narrative; component/logic code.
- JSON overlays are written UTF-8 with **LF** endings (Node writes flip to CRLF; git wants LF — normalise before staging).
- Baseline: 39 frontend vitest green; `check-i18n` OK (724 keys × 12 overlays); vite build clean.

---

### Task 1: Acceptance-guard script

**Files:**
- Create: `frontend/scripts/check-copy-refit.mjs`
- Modify: `frontend/package.json` (add a `check:copy` script)

**Interfaces:**
- Produces: `npm --prefix frontend run check:copy` — exits 0 only when no refit file contains an em-dash and every overlay key's placeholder set matches the English dictionary value. Consumed by Tasks 2–4 as the gate.

- [ ] **Step 1: Write the guard script**

```js
// Sprint 10.191: acceptance guard for the impersonal copy refit. FAILS while any product-UI string still
// carries a stylistic em-dash "—", or when an overlay value has lost/gained a {placeholder} vs the English
// source. Zero-dependency. Run via `npm run check:copy`. legal.ts is intentionally not covered.
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
```

- [ ] **Step 2: Register the npm script**

In `frontend/package.json` `"scripts"`, add after `"check:legal"`:

```json
    "check:copy": "node scripts/check-copy-refit.mjs",
```

- [ ] **Step 3: Run it — expect RED (em-dashes exist today)**

Run: `node frontend/scripts/check-copy-refit.mjs`
Expected: FAIL, reporting em-dashes in `i18n.ts` and many overlays.

- [ ] **Step 4: Commit**

```bash
git add frontend/scripts/check-copy-refit.mjs frontend/package.json
git commit -m "test: acceptance guard for impersonal copy refit (no em-dash + placeholder integrity) (10.191)"
```

---

### Task 2: HR + EN refit in `i18n.ts` (the reference voice)

**Files:**
- Modify: `frontend/src/i18n.ts`

**Interfaces:**
- Produces: the impersonal, em-dash-free `hr`/`en` gold-standard values that Task 3's per-language agents mirror.

- [ ] **Step 1: Rewrite every affected `hr` and `en` value in place**

For each dictionary entry, apply the transformation rules from the spec. Do NOT change keys or placeholders. Concrete patterns (not exhaustive — apply the same logic everywhere):

- `form.vibeHeading` — hr `'Ne znaš opisati stil? Odaberi ugođaj.'` → `'Stil se teško opisuje? Pomaže odabir ugođaja.'`; en `'Not sure how to describe your style? Pick a vibe.'` → `'Style hard to put into words? A vibe helps.'`
- `planner.subnavTagline` — hr `'Opiši prostor, postavi budžet i dobij popis za kupnju.'` → `'Opis prostora i budžet daju popis za kupnju.'`; en `'Describe the space, set a budget and get a shopping list.'` → `'A space description and a budget produce a shopping list.'`
- `moveIn.needRooms` — hr `'Odaberi barem jednu sobu.'` → `'Potrebna je barem jedna soba.'`; en `'Pick at least one room.'` → `'At least one room is required.'`
- `planner.aiUnderstood` — hr `'AI je razumio tvoju želju'` → `'Želja je prepoznata'`; en `'The AI understood your wish'` → `'Wish understood'`
- Em-dash connectors — `'Katalog za ovu državu se još puni — prikazujemo opće prijedloge.'` → `'Katalog za ovu državu se još puni, pa su prikazani opći prijedlozi.'` (comma / period / colon per context, or restructure). Do this for every `—`, including the few in `i18n.ts` code comments (change those to `-` or a comma) so the guard's whole-file check is clean.
- Control labels that are imperatives → noun phrases in HR (`'Odaberi sobe'` → `'Odabir soba'`); leave already-neutral English button verbs (`Save plan`, `Copy`, `Open`) as they are.

- [ ] **Step 2: Verify HR/EN are clean; overlays still fail (expected)**

Run: `node frontend/scripts/check-copy-refit.mjs`
Expected: the `i18n.ts` em-dash line is GONE; failures now come only from the 12 overlays (their em-dashes) — that is Task 3. No `i18n.ts`-sourced placeholder failure.

- [ ] **Step 3: Build + tests still pass**

Run: `npm --prefix frontend run build` (expect exit 0) and `npm --prefix frontend test` (expect all green — confirm no test asserts a changed string; if one does, it is asserting copy and should be updated to the new value).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/i18n.ts
git commit -m "copy: impersonal HR/EN voice + em-dash removal in the i18n dictionary (10.191)"
```

---

### Task 3: The 12 overlays via a Workflow

**Files:**
- Modify: `frontend/src/messages/{de,it,fi,fr,nl,sk,es,pt,no,sv,da,sl}.json`

**Interfaces:**
- Consumes: the finished HR/EN pairs in `i18n.ts` (gold standard), the guard from Task 1.

- [ ] **Step 1: Run the refit Workflow**

Author and run a Workflow (`Workflow` tool) that pipelines over the 12 languages. For each language `L`:
- **Rewrite stage** — an agent opens `frontend/src/messages/L.json`, and for each key rewrites the value into L's own impersonal/neutral register following the spec rules (no 2nd-person address, no user-possessive, controls → noun labels, every "—" replaced by natural punctuation), using the matching `hr`/`en` value in `frontend/src/i18n.ts` as the meaning + tone reference. It MUST keep every key, keep JSON valid, and keep every `{placeholder}` exactly. It edits the file in place (each language is a different file, so no conflict).
- **Verify stage** — a second agent re-reads `L.json` and returns a structured report: any key still holding an em-dash, any lost/added placeholder vs the English source, any residual second-person marker in L, and a natural-tone judgement. The report drives fixes.

Return the list of `{language, remainingIssues}` so the main loop knows which files need a hand-fix.

- [ ] **Step 2: Hand-fix anything the verify stage flagged**

For each flagged key, open the file and correct it (drop the em-dash, restore the placeholder, or fix the phrasing).

- [ ] **Step 3: Normalise line endings to LF**

Node/agent writes may have introduced CRLF. Normalise the 12 files:

Run (PowerShell): for each `messages/*.json`, read and re-write with `-Encoding utf8` and `"\n"` joins, OR `git add --renormalize` if `.gitattributes` enforces LF. Confirm the staged diff is value-only (no whole-file EOL churn) with `git diff --cached --stat`.

- [ ] **Step 4: Guard + parity pass**

Run: `node frontend/scripts/check-copy-refit.mjs` (expect `check-copy: OK`) and `node frontend/scripts/check-i18n.mjs` (expect `check-i18n: OK — 724 … keys present`).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/messages
git commit -m "copy: impersonal voice + em-dash removal across the 12 locale overlays (10.191)"
```

---

### Task 4: Full verification + housekeeping

**Files:**
- Modify: the memory files (new `sprint-10-191` note + `MEMORY.md` index line)

- [ ] **Step 1: Full frontend gate**

Run, expecting all green:
- `node frontend/scripts/check-copy-refit.mjs` → `check-copy: OK`
- `node frontend/scripts/check-i18n.mjs` → `check-i18n: OK`
- `npm --prefix frontend run build` → exit 0
- `npm --prefix frontend test` → all vitest pass

- [ ] **Step 2: Visual spot-check**

`preview_start` the frontend dev server, load the HR locale and one other (e.g. DE), and read the hero, planner form, Move-In panel and results copy via `read_page` — confirm the voice is impersonal and no "—" renders. Screenshot for the owner.

- [ ] **Step 3: Record the sprint**

Write `memory/sprint-10-191-impersonal-copy-refit.md` (what changed, the guard script, the legal exclusion, the LF gotcha) and add its one-line index entry to `MEMORY.md`.

- [ ] **Step 4: Final commit**

```bash
git add -A && git commit -m "chore: record 10.191 impersonal copy refit"
```

---

## Self-review

**Spec coverage:** impersonal voice → Tasks 2–3; em-dash removal → Tasks 1–3 (guard) ; all-15-languages → Task 2 (hr/en) + Task 3 (12 overlays); legal excluded → not in any task's file list; placeholder/key preservation → Task 1 guard + `check-i18n`; LF EOL → Task 3 Step 3; verification → Task 4. No spec requirement is unaddressed.

**Placeholder scan:** the one real code artefact (check-copy-refit.mjs) is given in full; the copy tasks give concrete before→after examples plus the rule to apply everywhere (a copy task can't enumerate all ~700 strings, but the rule + the guard + the HR/EN gold standard make each edit unambiguous). No TBD/TODO.

**Type consistency:** `check:copy` / `check-copy-refit.mjs` / `placeholders()` / `enByKey` are used with the same names across tasks. The guard's em-dash constant `—` matches the spec's "em-dash only" scope.
