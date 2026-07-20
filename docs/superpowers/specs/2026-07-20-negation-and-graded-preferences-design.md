# Negation handling and graded preferences — design (2026-07-20)

Sprint 10.190. Owner ask, verbatim intent: *"kreni na negacije, ali trebamo pokrit sve moguće kombinacije … takve stvari moramo izbjeć."* Follow-up refinement: *"kad korisnik kaže jeftinije ne treba bit najjeftinije nego samo manje cjenovno; i kad kaže jeftino nek proba naći kvalitetno; ako kaže ne previše moderno nek nađe blagi modern ali classy look."*

## Context

AI is off by default (`BUDGETSPACE_BETA_MODE`), so the deterministic regex parsers in `PlannerIntentExtractor`, `KitchenIntentClassifier` and `AmountParser` are the path real users actually hit. Sprint 10.189 closed 99 multilingual synonym gaps but explicitly left a known limitation: **the parsers are negation-blind**. A matched token sets its value regardless of any surrounding negation.

Confirmed present-day behaviour (read from the code, not assumed):

| Dimension | Negation aware? |
|---|---|
| Categories (`applyCategories`) | **Yes** — `CLAUSE_TRIGGER` have/exclude groups + `REVERSE_EXCLUDE` for object-verb order |
| Retailer intent (`applyRetailerIntent`) | Partly — exclude/prefer windows exist, but **double negation is not handled** |
| Room, style, furnishing level, optimization goal, colours/materials | **No** |
| `KitchenIntentClassifier` COMPLETE branch | **No** |
| Frontend `outOfScope.ts`, `multiRoom.ts` | **No** |

Two further findings that shape this work:

- `PlannerService.score(...)` maps **both** "jeftino" and "najjeftinije" to `lowest-price`, which applies `priceBias = max(0, 34 - price/18)` — a pure floor-seeking pull. Rating contributes only `rating * 5` (max ~25), so price dominates. There is no middle setting.
- `style` is a single enum and `styleMatches` is binary (`38` on match, `12` otherwise). A blended "mild modern but classy" result is **not expressible** in the current model.

## Goals

1. A negated signal is never applied as if asserted, across every dimension, every market language, both word orders, and double negation.
2. "cheaper" and "cheapest" become distinct intents, and both keep looking for quality within their price band.
3. "not too X" produces a genuine blend of X and its complement rather than a suppression or a flip.

## Non-goals

- No sentiment/NLP model. This stays deterministic and reviewable.
- No change to the AI path's schema.
- No new user-facing form control for style blending — it is prompt-derived only.
- Not touching `applyCategories`' existing negation logic beyond adding double negation.

---

## Part A — `NegationScope`

A new pure class `backend/src/main/java/ai/budgetspace/planner/NegationScope.java`, unit-testable in isolation.

```java
final class NegationScope {
    static NegationScope of(String normalizedText);
    boolean isNegated(int matchStart);
}
```

**Input contract:** the already-normalized text (lower-cased, accent-stripped, Nordic ligatures folded) that `PlannerIntentExtractor.normalize` produces.

### Cue table

Word-bounded (`\b…\b`) for every short or ambiguous token, so HR `ne` never fires inside "nema"/"neutralno"/"nekoliko".

| Language | Cues |
|---|---|
| HR/BS/SR | `ne`, `bez`, `nemoj`, `necu`, `necemo`, `nikako`, `nije`, `nisam` |
| SI | `ne`, `brez`, `nocem` |
| SK | `nie`, `bez`, `nechcem` |
| EN | `not`, `no`, `dont`, `doesnt`, `without`, `nothing`, `never`, `avoid`, `skip` |
| DE | `nicht`, `kein\w*`, `ohne`, `nie` |
| IT | `non`, `senza`, `niente`, `mai` |
| ES | `no`, `sin`, `nada`, `nunca` |
| PT | `nao`, `sem`, `nada`, `nunca` |
| FR | `ne`, `pas`, `sans`, `aucun`, `jamais` |
| NL | `niet`, `geen`, `zonder`, `nooit` |
| FI | `ei`, `ala`, `ilman` |
| SV | `inte`, `ingen`, `inga`, `utan`, `aldrig` |
| NO | `ikke`, `ingen`, `uten`, `aldri` |
| DK | `ikke`, `ingen`, `uden`, `aldrig` |

### Scope rules

1. A cue opens a span that runs to the next **clause boundary**: `,` `;` `.` `!` `?` or a **contrast conjunction** — `ali`, `nego`, `vec`, `but`, `aber`, `sondern`, `ma`, `pero`, `sino`, `mais`, `maar`, `mutta`, `men`, `ale`, `vendar`, `mas`.
2. A coordinating "and" (`i`, `and`, `und`, `e`, `y`, `et`, `en`, `ja`, `och`, `og`) does **not** close the span — "ne želim tamno i crno" negates both.
3. **Parity:** cues inside an open span toggle it. An even count is positive again, so "nicht ohne IKEA" resolves to a *preference* for IKEA.
4. **Cue-contained-token exemption:** a match is negated only when `matchStart >= cueEnd`. This is what keeps our own negative-shaped positive tokens working — `pas cher` (FR, cheap), `ne vise od dvije` (store limit), `bez obilazaka` (fewer stores) all *start at* the cue and are therefore left alone.

### Integration

`PlannerIntentExtractor.matches(text, regex)` gains a scope-aware sibling used by `applyRoom`, `applyStyle`, `applyFurnishingLevel`, `applyOptimizationGoal` and `applyColorAndMaterialPreferences`: it accepts a match only if `!scope.isNegated(matchStart)`.

`KitchenIntentClassifier.classify` applies the same check to the COMPLETE branch, so "ne želim kompletnu kuhinju, samo pećnicu" falls through to COMPONENT.

`applyRetailerIntent` keeps its own window logic and gains only the parity check, so "nicht ohne IKEA" flips exclude → prefer.

`AmountParser` is deliberately **not** scoped. Its negative-shaped phrases ("ne više od 1000", "ne preko 600") are already budget *verbs* that mean an upper bound, and a budget has no meaningful negated form.

Frontend: `multiRoom.ts` and `outOfScope.ts` get a small shared `negationScope.ts` port of the same rules (same cue table, same boundary set), so "ne trebam perilicu, samo namještaj" stops raising the appliances banner.

### Semantics

**Default is suppression** — a negated match simply does not apply, leaving the field at its default. Suppression can never invent an intent the user did not express.

**One explicit inversion**, because the owner called out the cheap↔quality relationship directly: a negated price-down intent ("neću jeftino", "not cheap", "keine billigen Möbel") sets `optimizationGoal = style-match`, which is the existing "prefer quality, spend toward budget" path. This is the only inversion in Part A; everything else suppresses. Parts B and C carry the remaining intelligence.

---

## Part B — Price intensity

### Parser

Split today's single bucket in two:

- **`lower-price` (new)** — comparative/plain: `jeftinije`, `jeftino`, `cheaper`, `cheap`, `gunstig`, `billig`, `barato`, `economico`, `pas cher`, `moins cher`, `bon marche`.
- **`lowest-price` (existing, now superlative only)** — `najjeftin`, `sto jeftin`, `cheapest`, `low cost`, `so gunstig wie moglich`, `lo mas barato posible`, `il piu economico`, `le moins cher possible`.

Where both appear, the superlative wins.

### Scoring (`PlannerService.score`)

- `lower-price` → `priceBias = max(0, 18 - price/40)`. A gentle downshift instead of a floor.
- `lowest-price` → unchanged pull, `max(0, 34 - price/18)`.
- **Quality within the band** (both cheap paths) → an extra `rating * 6` term, so a clearly better-rated piece beats one that is a few euro cheaper. This directly implements *"i kad kaže jeftino, nek proba naći kvalitetno"*.

Exact constants are starting points; they are tuned against the tests in Part D, not asserted as final.

### Surface impact

No new DTO field — `optimizationGoal` is a `String`, so this is a new value only. The frontend `OptimizationGoal` type gains `lower-price`, plus one hand-written label per locale (14 languages; no machine-sounding copy, per the standing rule).

---

## Part C — Style softening and blend

### Parser

Detect **negation + degree**, which is a distinct construct from plain negation:

`ne previse`, `nije pretjerano`, `ne bas` (HR) · `not too`, `nothing too`, `not overly` (EN) · `nicht zu` (DE) · `non troppo` (IT) · `no demasiado` (ES) · `nao muito` (PT) · `pas trop` (FR) · `niet te` (NL) · `ei liian` (FI) · `inte for` (SV) · `ikke for` (NO/DK)

On a match with style X: keep `style = X` and set `secondaryStyles = [complement(X)]`.

**This is checked before Part A's suppression** and marks its span as handled, so a softened style produces a blend rather than being silently dropped.

| X | complement |
|---|---|
| modern | classic |
| classic | modern |
| minimal | warm |
| warm | bright |
| bright | warm |
| industrial | warm |
| boho | minimal |

### DTO

Add `List<String> secondaryStyles` to the canonical `PlannerInputDto` record, defaulted to `List.of()` in **both** existing back-compat constructors (the 16-arg pre-10.7 and 19-arg pre-10.120 signatures) and in the `Builder`. This is the exact pattern already used for `colorPreferences` (10.7) and `quantities` (10.120), so every existing call site compiles unchanged and previously saved plans deserialize without migration. A `withSecondaryStyles(...)` builder method mirrors `withColorAndMaterialPreferences`.

### Scoring

Replace the binary style term:

| Case | Score |
|---|---|
| matches primary style | 38 (unchanged) |
| matches a secondary style | 30 (was 12) |
| matches **both** | 44 |
| matches neither | 12 (unchanged) |

Products already carry a `styleTags` list, so "both" is a real, checkable state — this is what produces "blagi modern ali classy".

### Frontend

`PlannerInput` gains optional `secondaryStyles?: string[]`. No UI control; it is prompt-derived and round-trips with the plan.

---

## Part D — Testing

- **`NegationScopeTest`** — one cue per language; clause boundary vs coordinating "and"; contrast conjunctions; double negation parity; the cue-contained-token exemption (`pas cher`, `ne vise od dvije`, `bez obilazaka` must survive).
- **`PlannerIntentExtractorTest`** — a negated case per dimension in several languages, each paired with its affirmative twin to prove the positive path still fires. Plus the "not cheap → style-match" inversion.
- **`KitchenIntentClassifierTest`** — "ne želim kompletnu kuhinju, samo pećnicu" → COMPONENT; "nicht ohne eine komplette Küche" → COMPLETE.
- **`PlannerIntentExtractorTest`** (Part B) — comparative vs superlative map to `lower-price` vs `lowest-price` across languages.
- **`PlannerServiceTest`** — `lower-price` yields a higher total than `lowest-price` for the same budget; between two similarly-priced candidates the better-rated one is picked in a cheap plan; a softened style prefers a product carrying both tags.
- **`PlannerInputDtoTest` / back-compat** — both legacy constructors still compile and default `secondaryStyles` to empty.
- **Frontend vitest** — negated out-of-scope raises no banner; negated room words do not trigger the multi-room nudge.
- **`prompt-matrix.json`** — a new `negation` register so the data-driven matrix covers this class too.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Part B changes scoring, so existing plan-total assertions may shift | Each shifted assertion is re-verified individually against the intended behaviour. No blanket "update until green". |
| Over-broad negation scope suppresses a legitimate signal | Clause-bounded scope + the cue-contained exemption + an affirmative twin assertion for every negated test case. |
| Short cues (`ne`, `no`, `ei`, `pas`) misfire | Word-bounded, and every one gets an explicit false-positive guard test. |
| New `lower-price` value reaches an old frontend build | It is additive; an unknown goal falls back to the existing default rendering path. |

## Follow-up, explicitly out of scope

The 10.189 memory records the negation limitation as accepted; that note must be updated once this ships. Three dated code comments from 10.189 read `2026-07-18` and should read `2026-07-20` — corrected in passing since the same files are touched here.
