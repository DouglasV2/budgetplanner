# Kitchen planning — Increment 1: Complete‑kitchen (modular sets) + foundation

- **Date:** 2026-07-10
- **Status:** Draft for owner review
- **Owner reframe:** kitchen = *planning/buying a real kitchen*, not kitchenware.

## 1. Context & problem

Today the app **deliberately refuses complete kitchens**. `isFittedKitchenIntent`
(`frontend/src/components/PlanResults.tsx:41`, Sprint 10.102) detects "fitted/built‑in kitchen" prompts and
shows a note (`results.kitchenFittedNote`) pointing the user to IKEA's kitchen planner; the backend otherwise
builds a normal *freestanding* kitchen plan (carts, shelves, lighting). The kitchen catalog contains **only
furniture/fittings** — zero cabinets, appliances, sinks, worktops, or complete kitchens.

The owner reframed the kitchen scope (2026-07-10). Kitchen is planning a real kitchen, with three intents:

- **A — Complete kitchen** (*cijela kuhinja*): the whole thing as a package.
- **B — Individual components**: kitchen furniture (cabinets/islands), appliances (built‑in/freestanding),
  sink area, work surfaces & install parts.
- **C — Kitchenware** (cookware/tableware/utensils/food‑storage): **secondary, opt‑in only, never
  auto‑injected** when a user just says "kitchen."

This spec covers **Increment 1 only**: the foundation + the **Complete‑kitchen (modular sets)** mode.

## 2. Goals (this increment)

1. Detect **complete‑kitchen** intent and, instead of the current dead‑end, return real, priced, buyable
   **modular kitchen sets** (+ any clearly‑labelled kitchen elements we have), with an honest
   modular‑not‑fitted note that still links IKEA's planner for true made‑to‑measure kitchens.
2. Lay the **foundation**: kitchen intent routing (A/B/C) + a minimal taxonomy (`kitchen-set`) + parsed
   kitchen attributes surfaced as "what we understood."
3. **Source** web‑verified modular kitchen sets for **DE, AT, HR** (no fabrication).

## 3. Non‑goals (this increment — deferred to later increments)

- Component sourcing — cabinets/appliances/sink/worktops (increments 3–5).
- A bundled *"set + appliances + worktop = one total"* — needs the component layer; doing it now would
  invent numbers.
- The full component taxonomy (~20 categories) — each category lands **with** its sourcing, not up front.
- Kitchenware (intent C) — increment 6.
- **Hard‑filtering** on kitchen shape (modular‑set data rarely carries shape).
- A fitted‑kitchen **configurator** — permanently out of scope; we link IKEA's planner for that.

## 4. Design

### 4.1 Intent routing (deterministic, multilingual, backend)

Add `KitchenIntentClassifier.classify(prompt) → COMPLETE | COMPONENT | KITCHENWARE | NONE`.

- Deterministic keyword matching (diacritics stripped, same approach as the existing pattern), **HR/DE/EN
  first**, extended to the other active languages as keywords are confirmed.
- Repurposes/absorbs the current `FITTED_KITCHEN_PATTERN`. Example seed keywords:
  - **COMPLETE:** cijela/kompletna kuhinja, opremam/uređujem kuhinju, kuhinja u paketu, modularna kuhinja,
    komplette Küche / Einbauküche / Küchenzeile, fitted/modular/complete kitchen, kitchen renovation,
    KNOXHULT, ENHET (as a kitchen), METOD.
  - **COMPONENT:** perilica posuđa/Geschirrspüler/dishwasher, pećnica/Backofen/oven, ploča/hob/kochfeld,
    hladnjak/Kühlschrank/fridge, napa/hood, sudoper/Spüle/sink, slavina/faucet, radna ploča/worktop,
    ormarić/Schrank/cabinet, ladice/drawers…
  - **KITCHENWARE:** posuđe/cookware, tanjuri/plates, pribor/cutlery, čaše/glasses…
- **Only `COMPLETE` changes behaviour this increment.** `COMPONENT` and `KITCHENWARE` are recognised (so the
  classifier is complete and testable) but routed to today's behaviour — placeholders we fill in later
  increments. This keeps the surface small while making the routing real and tested.
- **Location:** backend, because it drives the plan output. The frontend `isFittedKitchenIntent` note is
  superseded for the `COMPLETE` path (the backend now returns real results + the honest note); it may remain
  as a harmless client fallback until removed.

### 4.2 Complete‑kitchen output (honest modular sets)

A new backend path (a `KitchenPlanner` collaborator, or a `buildCompleteKitchen(...)` method on
`PlannerService`) that, when intent = `COMPLETE`:

- selects **modular kitchen sets** (catalog `category = kitchen-set`) for the request's market, within budget,
  ranked by fit (style/finish/size where the set's data supports it);
- returns them as product cards in a dedicated **"Kompletna kuhinja"** result section, each with real
  price/retailer/URL/image (reusing the existing product‑card + no‑fabrication image rules);
- always shows the **honest note**: modular sets are *not* a configured made‑to‑measure kitchen; for that, use
  IKEA's planner (market‑aware URL via the existing `ikeaMarketUrl`);
- if no set fits the budget, shows an **honest empty state** ("no complete kitchen under {budget} — here are
  the closest sets / try a higher budget"), never a fabricated result.

**Response shape:** a set *is* a `ProductDto`, so add an optional `completeKitchen` section to
`PlanGenerationResponse` (`{ sets: List<ProductDto>, understanding, showModularNote }`) — reusing the existing
generate entry point and result view (one flow, one door). The UI renders it **inline** on the existing result
page (owner decision — §10).

### 4.3 Kitchen attributes = understanding, not hard filters

Parse and surface (never hard‑filter this increment):

- **budget**, **size** — already parsed.
- **shape** (new enum): `single-wall | l-shaped | u-shaped | galley | island | unknown`, multilingual keywords.
- **style** — already parsed; **finish/colour** — via existing `colorTags`.
- **includeAppliances** (new boolean‑ish flag): "s uređajima / with appliances / bez uređaja / without".

These render as a **"što smo razumjeli"** chip and rank/annotate sets where the set's own data supports it
(e.g. a set sold "with appliances" vs "without"), plus honest caveats ("razumjeli smo da želiš uređaje — ovi
setovi ih uključuju / za pojedinačne uređaje vidi komponente [uskoro]"). No false precision.

### 4.4 Taxonomy (lean — one category now)

Add exactly one category this increment: **`kitchen-set`** (a complete/modular kitchen unit).

Wiring points (kept in sync):
1. `backend/.../product/ProductTaxonomy.java` → `KNOWN_CATEGORIES` (+ `CATEGORY_ALIASES`:
   `modular-kitchen`, `kitchen-package`, `complete-kitchen` → `kitchen-set`).
2. `frontend/src/types/index.ts` → `ProductCategory` union.
3. Category labels (backend label + frontend `categoryLabels`).
4. The complete‑kitchen path (§4.2). **Note:** `kitchen-set` is used by the *complete‑kitchen* flow, **not**
   injected into the normal freestanding kitchen room flow (`CATEGORY_FLOW_BY_ROOM["kitchen"]` stays as is), so
   a plain "furnish my kitchen" (freestanding) plan is unaffected.

### 4.5 Sourcing

Web‑verified **modular kitchen sets** for **DE, AT, HR** — IKEA is present in all three and sells real,
priced modular kitchens (KNOXHULT, ENHET kitchens, complete‑kitchen packages). Strict no‑fabrication: real
product URL + price + verified image/source or the row does not ship (follow the existing catalog‑sourcing
discipline). New `catalog/real-ikea-kitchen-sets-<sprint>.json` + a `RealCatalogSeeder` entry; verified then
independently re‑verified (fetch → second agent re‑fetches), like prior catalog sprints.

### 4.6 UI

- On a `COMPLETE` result: a **"Kompletna kuhinja"** section of set cards + the honest note + the
  understanding chip. Reuses the product‑card patterns; upgrades the existing `kitchen-scope-note`.
- Localised copy (hr/en in `i18n.ts`; the 12 message JSONs follow, English‑fallback in the meantime).

## 5. Architecture / touched files

- **Backend:** `KitchenIntentClassifier` (new) or extend `PlannerIntentExtractor`; `KitchenPlanner` (new) or
  a method on `PlannerService`; branch inside the existing generate flow when intent = `COMPLETE`; an optional
  `completeKitchen` section on `PlanGenerationResponse`; `ProductTaxonomy` (kitchen-set);
  new catalog JSON + `RealCatalogSeeder` entry.
- **Frontend:** kitchen result rendering in `PlanResults` (or a small `CompleteKitchenResults`); `ProductCategory`
  + `categoryLabels`; i18n keys. No client classifier (the backend drives the mode).

## 6. Honesty & compliance

No fabricated sets/prices/URLs; **modular‑not‑fitted** framing kept prominent; IKEA planner linked for true
custom/fitted; only real DE/AT/HR sourcing; images shown only when verified (existing rule).

## 7. Testing

- **Unit:** `classify` across HR/DE/EN for COMPLETE/COMPONENT/KITCHENWARE/NONE (incl. tricky/garbage → NONE);
  complete‑kitchen selection (within budget, ranked, excludes non‑`kitchen-set`, market‑scoped, honest empty
  when nothing fits); `kitchen-set` taxonomy validation; attribute parsing (shape/includeAppliances).
- **Live:** DE/AT/HR complete prompts → real modular sets + note; a plain freestanding‑kitchen prompt is
  unchanged; a component/kitchenware prompt routes to today's behaviour (no regression).

## 8. Analytics

New events: `kitchen_intent` (value = complete/component/kitchenware), `complete_kitchen_view`,
`kitchen_set_click` (+ reuse/extend `product_click`), so we can see whether the complete‑kitchen mode is used.

## 9. Roadmap (context — later increments)

1 (this) → 3 components: cabinets/islands (DE/AT/HR) → 4 components: appliances (**new retailers** —
MediaMarkt‑class, built‑in vs freestanding) → 5 sink area + worktops/install → 6 kitchenware (opt‑in
secondary) → 7 cross‑market port.

## 10. Resolved decisions

- Complete‑kitchen shows **sets to choose from**, not a bundled single total (bundle needs components).
- Shape/include‑flags are **understanding + honest caveats**, not hard filters, this round.
- **Only `kitchen-set`** added to the taxonomy now; the rest come with their sourcing.
- Markets: **DE + AT and HR** first for sourcing.
- **UI placement (owner, 2026-07-10):** an **inline "Kompletna kuhinja" section** on the normal result page —
  reuse `/generate` + `PlanResults`, one flow. (Not a separate Move‑In‑style mode.)
- **Sources (owner, 2026-07-10):** **IKEA‑only** modular sets this increment (KNOXHULT/ENHET/packages) for
  DE/AT/HR; local retailers deferred.
- **Sets only (owner, 2026-07-10):** strictly complete modular **sets** this increment; individual "labelled
  elements" (worktops/cabinets) move to the component increments (3–5) with their taxonomy + sourcing.

## 11. Open questions for the owner

_None — all resolved above (see §10, owner decisions 2026-07-10)._
