# Impersonal copy refit + em-dash removal — design (2026-07-21)

Sprint 10.191. Owner ask: *"treba bit app u trećem licu… kao primjer 'Želiš opisati stil? Odaberi ugođaj' i da to pretvorimo u treće lice… i gdje god ima dva dashes -- to treba maknut jer je to tipično AIevski."*

Two changes to the app's copy, applied together because they touch the same strings:
1. **Impersonal voice** — stop addressing the user as "ti"/"you"; describe and name instead of command.
2. **Remove the em-dash "—"** used as a stylistic sentence connector (the classic "AI-written" tell).

## Decisions taken during brainstorming

- **Voice = impersonal / neutral** (owner picked this over a formal Vi-form and an app-narrates voice). No direct address, no possessives; controls become concise noun labels; descriptions become impersonal statements. It must still read as **hand-written, warm-but-neutral human copy**, never stiff machine text — this extends the standing owner rule (no AI-vibecoded copy; see the `no-ai-vibecoded-copy` and `editorial-redesign` memories).
- **Scope = all 15 market languages** (owner chose this over "HR+EN first").
- **English control labels stay natural** — a button like `Save plan` is already neutral and stays; do not force an awkward nominalisation (`Saving plan`).

## Context

The copy is centralised. `frontend/src/i18n.ts` is the DICTIONARY: ~724 keys, each with an inline `hr` and `en` value, and it defines the key set. The 12 files in `frontend/src/messages/*.json` are the per-locale overlays (de, it, fi, fr, nl, sk, es, pt, no, sv, da, sl), one value per key. There are ~700 em-dashes across all these files (139 in `i18n.ts`, ~40–54 per overlay). A `.tsx` grep found **no** hardcoded second-person Croatian strings in components, so the surface is exactly these 13 files.

## Goals

1. Zero direct second-person address (ti/vi/du/Sie/tu/vous/you/…) and zero user-possessives in the product UI copy of all 15 languages.
2. Zero em-dash "—" remaining in the refit files (replaced by the right punctuation or a restructured sentence — never a bare hanging dash).
3. Meaning, tone quality, interpolation placeholders and key parity all preserved.

## Non-goals

- **`legal.ts` is out of scope** — the Privacy/Terms documents are formal legal prose in a different register, and rewriting them carries liability. The cookie/consent *banner* keys that live in `i18n.ts` are product UI and ARE refit.
- No new or removed keys; no logic, component or backend changes.
- The backend HR narrative (`describePlan`/`advisorNote`) is a separate concern (mostly dead code per the `no-ai-vibecoded-copy` memory) and is not touched here.
- En-dash "–" / hyphen "-" in ranges and compounds (e.g. "TV-komoda", "10–20") are NOT the target; only the em-dash "—" connector is.

## Transformation rules

Applied per string, keeping the meaning and a natural human tone:

| Current (2nd person) | Impersonal |
|---|---|
| Imperative to the user — "Odaberi sobe", "Opiši prostor", "Pick the rooms" | Noun label for a control — "Odabir soba"; a neutral statement for a description — "Opis prostora" |
| Possessive — "tvoj račun", "your market", "svoj prostor" | Drop or generic — "račun", "tržište", "prostor" |
| "dobiješ / dobij popis", "you get a list" | "gotov popis", "a shopping list" |
| Question at the user — "Ne znaš opisati stil?" | Impersonal — "Stil se teško opisuje?" |
| Connector em-dash — "besplatni — AI uključen" | Punctuation — "besplatni, s uključenim AI-jem" (comma / period / colon), or restructure |

Worked example (the owner's): `form.vibeHeading` "Ne znaš opisati stil? Odaberi ugođaj." → "Stil se teško opisuje? Pomaže odabir ugođaja."

## Architecture / execution

Copy-only edits to 13 data files. No code.

1. **HR + EN by hand** in `i18n.ts` — this is the reference voice. Every `hr`/`en` value that addresses the user or carries an em-dash is rewritten in place.
2. **The 12 overlays via a Workflow**, one agent per language. Each agent receives its file's full `{key: value}` map plus the transformation rules and the finished HR/EN pairs as the gold standard, and returns a new value for EVERY key (unchanged verbatim when a string already has no address and no dash). One agent = one language keeps grammar within a single fluent context.
3. **Verify stage** (adversarial, per file): (a) key parity — exactly the input keys, none added/dropped; (b) placeholder integrity — every `{token}` in the source is present in the output; (c) no "—" remains; (d) no residual second-person marker from that language's list; (e) the copy reads naturally. Anything failing is regenerated or hand-fixed.
4. **Assemble + normalise** — write each overlay back as UTF-8 with LF line endings (Node writes flip to CRLF; git wants LF — normalise before staging, per the `sprint-10-188` memory), then re-check.

## Testing / verification

- `node frontend/scripts/check-i18n.mjs` — proves key parity across all 12 overlays + the dictionary (this is the hard gate; a dropped key fails here).
- A one-off check that no refit file contains "—" and that no source placeholder was lost (scriptable grep/diff).
- `npm --prefix frontend run build` — tsc + vite clean.
- `npm --prefix frontend test` — vitest green (no test asserts a copy string that changes; confirm during execution).
- Visual spot-check of the rendered HR + one other locale (hero, planner form, move-in, results) in the browser preview.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Agent copy quality in a language I can't read | The verify stage checks parity/placeholders/dashes/2nd-person mechanically (language-independent); HR/EN gold standard anchors the tone; the diff is reviewed for those mechanical properties before staging. |
| A dropped/renamed key breaks the app | `check-i18n` is a hard gate; the verify schema forces exactly the input key set. |
| A lost `{placeholder}` renders a broken string | Verify stage asserts placeholder integrity per key. |
| Stiff, robotic result (the very thing being removed) | Rules explicitly require natural human tone; HR/EN done by hand as the model; spot-check. |
| CRLF churn bloating the diff | Normalise to LF before staging; the diff should be value-only. |
