# Move-In QoL Final Touch — Design Spec

**Date:** 2026-07-13
**Branch:** `move-in-qol-final-touch` (worktree), forked from `d4a8344` (= `main` = `origin/main`).
**Scope:** Bounded final quality-of-life sprint for the existing "Cijeli stan" / Move-In whole-apartment planner. Not a redesign, not a new engine. Extend existing seams.

---

## 0. Context, git, and WIP handling

- **Base commit `d4a8344`** was chosen over the task's stated `4acf658` because the banner task depends on the `kauc.png` hero introduced in later commits (`3f927ab`, `d98870b`). `d4a8344` is the verified HEAD and is identical to `main`/`origin/main`.
- **Owner WIP left untouched.** The main checkout has uncommitted "Sprint 10.183 — honest replace" WIP (backend `bestReplacement` retry + "no nicer/cheaper option" inline notes) touching `PlannerService.java`, `PlanResults.tsx`, `Planner.tsx`, `i18n.ts`, `styles.css`, and changing `onReplace → Promise<boolean>`. This worktree is clean from `d4a8344` and does **not** include it. A reconciliation guide ships in the final report.
- **No merge/deploy.** Work stays on the branch; landing on `main` requires the owner's explicit OK at the end.

## 1. Architecture found (audit summary)

**Frontend:** `Planner` is the single hub; owns `scope: 'single'|'apartment'`; mounts `PlanResults` (single) and `MoveInPlanner` (apartment) side by side via `hidden`. The apartment plan is `results: RoomPlanResult[]` local state in `MoveInPlanner` — **not persisted** (only `{rooms,budget}` in `localStorage['budgetspace.moveInDraft']`). Already has a grand-total card and an `aggregateByStore` retailer rollup. Per-product **lock** (`input.lockedProductIds`) is real, backend-wired, survives regen + saves — but single-room only; `MoveInPlanner.buildRoomInput` zeroes it.

**Backend:** `POST /api/plans/generate-move-in` → `PlannerService.generateMoveIn`. `MoveInRequestDto(base, rooms, totalBudget)` → `MoveInResponse(rooms[], grandTotal, totalBudget, apartmentPartial, shortfall)`; `MoveInRoomDto(roomType, allocatedBudget, plans[], partial)`. Budget split = `allocateMoveIn` (static, unit-tested by `MoveInAllocationTest`) over hardcoded `MOVE_IN_WEIGHTS`. Each room = one normal single-room build (`generateSeeded`), rooms coupled only by palette seeding + a leftover re-share. **No per-room regenerate endpoint.** Degraded honesty already computes `missingImportantCategories` (room-required) + `unavailableRequestedCategories` (explicit-unmet, fixture-facet accurate) but only a `partial` boolean reaches `MoveInRoomDto`. Saved plans = frozen JSON blobs; apartment = N per-room `saved_plans` rows grouped by `space_name` (no first-class apartment entity; adding one = V5 migration = out of scope). Adding **optional** DTO/JSON fields is backward-compatible (records + `FAIL_ON_UNKNOWN_PROPERTIES` off + existing legacy-constructor pattern).

## 2. Cross-cutting decisions (approved)

- **Persistence: `localStorage`.** Apartment session (generated `results`, priorities, kept rooms/products, purchased checklist) persists in `localStorage`, not a new DB entity. Survives reload + navigation; not cross-device/account (documented limitation). The existing per-room "Save whole apartment" stays.
- **Tests: zero-dep node scripts + browser.** No test framework added. An i18n-completeness guard (`.mjs`) + pure-logic node checks (TS transpiled via the esbuild already bundled with vite) + DOM/browser verification for UI. Backend keeps its JUnit suite (`mvn test`).
- **Copy:** every new string hand-written, human, Croatian-first (owner's "no AI/vibecoded copy" rule). Croatian strings are fixed verbatim by the task (§9). No robotic labels ("Optimiziraj", "Sekundarna faza", "Može kasnije", etc.).

## 3. Feature 1 — Room priorities (real allocation, not decoration)

**Model:** 3 levels per selected room — `now` (Treba odmah), `soon` (Želim uskoro, default), `later` (Nije hitno).

**Frontend:** a "Što ti treba prvo?" control in `MoveInPlanner` (a compact 3-way segmented control per selected room, mobile-tappable). Priority map in `MoveInPlanner` state + persisted (market-agnostic) draft. Sent to the backend on generate.

**Backend (engine change):**
- `MoveInRequestDto` gains optional `Map<String,String> roomPriority` (roomType → `now|soon|later`) via a new 4-arg constructor (3-arg legacy retained → back-compat).
- Priority factor (tunable constants): `now→1.5`, `soon→1.0`, `later→0.6`. `effectiveWeight(room) = MOVE_IN_WEIGHTS.getOrDefault(room,1.0) × factor`.
- `allocateMoveIn` gains a parallel `double[] priorityFactors` (default all-`1.0` ⇒ byte-identical to today):
  - **Feasible (Σfloors ≤ total):** every room still reserves its core-essentials floor (so `soon`/`later` still get usable plans); the leftover is distributed by `effectiveWeight` ⇒ `now` rooms get more discretionary budget.
  - **Infeasible (Σfloors > total):** floors are reserved in **priority order** (`now` first, then `soon`, then `later`, tie-break by weight then input order) until budget runs out ⇒ `now` rooms never lose essentials to lower-priority rooms; `later` rooms degrade to a reduced allocation → honest partial/missing state.
- Invariant preserved: `grandTotal ≤ totalBudget`; never overspends to fill `later` rooms.
- **Tests (`MoveInAllocationTest`, static, no catalog):** feasible-shifts-leftover-to-`now`; infeasible-reserves-`now`-floor-first-and-degrades-`later`; no-priority == legacy.

## 4. Feature 2 — Keep a room / product

**Keep a product** — reuse the existing `lockedProductIds` seam (backend-pinned, survives regen).
- Stop zeroing it in `moveInRoomInput` (backend) and `buildRoomInput` (frontend); thread a per-room `lockedProductIds` through `swapItem` and the adjust flow.
- Per-item toggle "Zadrži ovaj proizvod" / tooltip / "Opet mijenjaj proizvod". Retained products stay in room, count in totals, are excluded from whole-plan adjustments, remain explicitly removable. Keyed by `product.id`.

**Keep a room** — new concept: `retainedRooms: Set<RoomType>` in `MoveInPlanner`.
- Per-room header toggle "Zadrži ovu sobu" / hint / "Opet mijenjaj ovu sobu". A retained room keeps its products + total, still counts against the apartment budget, and is skipped by every whole-plan adjustment and regeneration.

**Guards:**
- Retained items exceed a new budget → honest message (`moveIn.retainedExceedsBudget`); never silently drop or overspend.
- Market/currency change → clear retained items whose `product.market` ≠ new market (never keep foreign-market products); explain via `moveIn.retainedMarketCleared`.

## 5. Feature 3 — Adjust the whole apartment

**UI:** an action area "Što želiš promijeniti?" + hint "Promijenit ćemo samo ono što nisi zadržao." + three buttons: "Smanji ukupnu cijenu" (with a target-total input), "Kupuj u manje trgovina", "Iskoristi ostatak budžeta". Not one vague "Optimiziraj".

**Backend:** one thin new endpoint `POST /api/plans/adjust-move-in` beside `generateMoveIn`, orchestrating existing primitives with retained-awareness. It does **not** blindly regenerate the apartment.
- Request: `MoveInAdjustRequest(PlannerInputDto base, List<AdjustRoomDto> rooms, int totalBudget, String action, Integer targetTotal, Map<String,String> roomPriority)`; `AdjustRoomDto(String roomType, FurnishingPlanDto plan, boolean retained, List<String> lockedProductIds)`. Action ∈ `reduce-total | fewer-stores | use-remaining`.
- Response: `MoveInResponse` + optional `boolean changed` + `String message` (honest no-op copy).
- Logic: retained rooms pass through untouched (spend subtracted from the pool). Non-retained rooms re-run `generateSeeded` with action-adjusted inputs, pinning per-room `lockedProductIds`:
  - **reduce-total:** re-allocate to `targetTotal − retainedSpend` (priority-aware), prefer smallest useful set of replacements (reuse `repairBudget`/`cheapestInCategory`). Honest when target < retained+essentials (`moveIn.adjustReduceUnreachable`).
  - **fewer-stores:** re-run non-retained rooms with `retailerMode=single`/`maxStores` + apartment-dominant retailer (`preferredRetailerForFewStores`); stay within budget; honest when store count can't drop (`moveIn.adjustFewerStoresNoop`).
  - **use-remaining:** `remaining = totalBudget − grandTotal`; if trivial, honest note; else distribute to non-retained rooms by priority, re-run with raised budgets, **keep only if it spends more within cap** (existing accept-guard), practical upgrades before décor; never exceed budget (`moveIn.adjustUseRemainingDone`).
- Market/currency and fixture subtype (bathtub↔shower) are preserved automatically (same engine: `marketCatalog` + `sameFixtureFamilyAndFacet`).
- **Frontend** splices the returned rooms back into `results`, keeping retained rooms; shows the honest message when `changed=false`.
- **Tests:** reduce-total minimal-diff + unreachable; fewer-stores consolidation-inside-budget + can't-reduce; use-remaining upgrades-within-cap; retained room/product preserved; no wrong fixture subtype; market isolation.

## 6. Feature 4 — Whole-apartment status

Extend the existing `.move-in-total-card` into a compact status overview at the top of the apartment results. Shows: total budget, current total, remaining ("Ostaje u budžetu"), #rooms, #retailers ("Kupnja u N trgovina"), rooms still needing attention ("Još treba riješiti" = `partial`/empty rooms), covered rooms ("Ove sobe su pokrivene"), and grouped missing items:
- **Treba za useljenje** ← room-required essentials missing (engine `missingImportantCategories`).
- **Dobro je dodati** ← available-but-absent optional categories (new small helper `niceToHaveMissing(input, plan)`; honest — omitted if empty).
- **Nije pronađeno za tvoje tržište** ← explicit market-unavailable (engine `unavailableRequestedCategories`).

**Backend:** surface these buckets onto `MoveInRoomDto` as optional fields (`missingEssential`, `niceToHave`, `unavailableInMarket`) — backward-compatible. No fake readiness score; the only number is the real total÷budget fill ratio, clearly a budget bar. A "Pokaži što još nedostaje" toggle reveals the grouped detail.

## 7. Feature 5 — Practical shopping checklist

Upgrade the existing by-retailer rollup into an interactive "Popis za kupnju" grouped by retailer:
- Retailer header "IKEA — N proizvoda — {total}"; per row: checkbox + product name + room + price + open-in-store link.
- Totals: "Kupljeno" (checked) / "Još za kupiti" (unchecked).
- Marking bought does **not** remove the item from the plan; purchased ≠ already-owned.
- `purchasedIds: Set<string>` in `MoveInPlanner`, persisted to `localStorage`, keyed so it survives normal navigation. No fake retailer checkout / no "order placed" claim.

## 8. Feature — Banner (kauc.png → banner.png full-bleed background)

`kauc.png` (the image) is referenced **only** in `PlannerHero.tsx` (the other "kauc" hit is an unrelated sofa-detection regex in `PlannerForm.tsx`).
- Remove `import kaucImg from '../kauc.png'` + the entire `.planner-hero-visual` block (img + placeholder SVG).
- `import bannerImg from '../banner.png'` (Vite-hashes + bundles it); set it as the `.planner-hero` background via inline `style` (hashed URL) + CSS for `background-size: cover`, balanced `background-position` (text over the open center), responsive height/position on mobile (sofa not behind text), restrained warm overlay/local gradient for legibility only. No dark overlay, bright gradient, extra card, heavy blur, or bordered panel. Content/buttons unchanged.
- Delete the now-unused `kauc.png` (no remaining references).
- Asset convention: existing images live at `frontend/src/` root (`kauc.png` was there) → keep `banner.png` at `frontend/src/`.
- Verify: tsc + vite build; desktop + mobile via browser; confirm `banner.png` fingerprinted into `dist/`.

## 9. Croatian copy (verbatim) + i18n

New keys added to `DICTIONARY` (hr+en) in `i18n.ts` **and** all 12 overlays (`messages/{de,es,fr,it,nl,pt,sl,sk,sv,no,da,fi}.json`), naturally translated (not literal MT).

| key | hr (fixed) |
|---|---|
| moveIn.priorityHeading | Što ti treba prvo? |
| moveIn.priorityHelp | Odaberi koje prostorije želiš riješiti prve. Tamo ćemo prvo usmjeriti budžet. |
| moveIn.priorityNow / prioritySoon / priorityLater | Treba odmah / Želim uskoro / Nije hitno |
| moveIn.keepRoom / keepRoomHint / unlockRoom | Zadrži ovu sobu / Ostatak stana možeš mijenjati bez promjena u ovoj sobi. / Opet mijenjaj ovu sobu |
| moveIn.keepProduct / keepProductHint / unlockProduct | Zadrži ovaj proizvod / Nećemo ga zamijeniti kad prilagođavaš ostatak plana. / Opet mijenjaj proizvod |
| moveIn.retainedExceedsBudget | Stavke koje želiš zadržati već prelaze novi budžet. Ukloni neku od njih ili povećaj budžet. |
| moveIn.adjustHeading / adjustHint | Što želiš promijeniti? / Promijenit ćemo samo ono što nisi zadržao. |
| moveIn.adjustReduce / adjustFewerStores / adjustUseRemaining | Smanji ukupnu cijenu / Kupuj u manje trgovina / Iskoristi ostatak budžeta |
| moveIn.adjustFewerStoresNoop | Ovaj plan već koristi najmanji realan broj trgovina za odabrani budžet. |
| moveIn.adjustUseRemainingDone | Iskoristili smo dio preostalog budžeta tamo gdje donosi najveću razliku. |
| moveIn.missingMoveIn / missingNiceToHave / missingNotFound | Treba za useljenje / Dobro je dodati / Nije pronađeno za tvoje tržište |
| moveIn.showMissing | Pokaži što još nedostaje |
| moveIn.stillToSolve / roomsCovered | Još treba riješiti / Ove sobe su pokrivene |
| moveIn.shoppingListHeading | Popis za kupnju |
| moveIn.bought / stillToBuy | Kupljeno / Još za kupiti |

(EN + 12 translations finalized in the i18n commit. Store-count plural handled in code for HR: 1 trgovina / 2–4 trgovine / 5+ trgovina.)

**Guard:** `frontend/scripts/check-i18n.mjs` (zero-dep): extracts `DICTIONARY` keys from `i18n.ts`, checks each overlay for missing/orphan keys, exits non-zero on gaps. Wired to an npm script (`check:i18n`) and runnable in CI.

## 10. State, compatibility, persistence

- **No leakage:** single-room vs apartment already use separate `localStorage` keys; apartment session stores `market` and drops market-specific `results`/`purchased` when the current market differs (priorities/kept-rooms are market-agnostic and restored).
- **Back-compat:** `MoveInRequestDto`/`MoveInResponse`/`MoveInRoomDto` gain **optional** fields only (legacy constructors; old frontend ignores new fields; new frontend tolerates null). Saved plans unchanged — old per-room saves open exactly as before. Old `localStorage` drafts hydrate with safe defaults. No API consumer broken.

## 11. Required tests

**Backend (`mvn test`):** priority allocation (feasible + infeasible) · retained room preservation · retained product preservation · retained-exceeds-budget · reduce-total minimal-diff + unreachable · fewer-stores consolidation-inside-budget + can't-reduce · use-remaining upgrades-within-cap · market/currency isolation · no wrong fixture subtype · degraded-capacity compat · old saved-plan (missing-field) deserialization · request validation for unknown ids/categories.

**Frontend (node scripts + browser):** i18n completeness guard · pure-logic checks (status computation, checklist totals, retained/priority state transforms, retained-exceeds-budget, saved-session hydrate defaults) · browser verification for priority selection, keep toggles, adjust buttons, checklist state, mobile layout, no stale state between plans, old drafts without new fields.

**Regression:** full backend suite · catalog · Move-In · Similar Items · Replace Product · degraded-capacity · state-transition · AI-fallback · frontend tsc + prod build · i18n guard · live backend boot smoke · representative Move-In API smokes (3-room limited budget with bedroom `now`; retain living room + reduce total; retain a sofa + fewer stores; use-remaining; NL bathroom stays honest; save/reopen; single↔apartment no leak).

## 12. Staged commits

1. Data model + back-compat (DTO optional fields, localStorage session schema + hydrate defaults, i18n guard scaffold).
2. Room priorities (allocation engine + UI control + allocation tests).
3. Keep room / product (locked-id threading + keep-room state + guards + tests).
4. Adjust-apartment actions (endpoint + UI + tests).
5. Status overview + shopping checklist.
6. Banner (kauc.png → banner.png).
7. i18n across all locales + accessibility + node-script tests.
8. Regression fixes.

## 13. Out of scope

3D / floor-plan / image-gen / new AI chat / subscription-paywall / retailer checkout / price-drop alerts / collaboration / plan comparison / saved-plan schema rewrite (new apartment entity or V5 migration) / redesign of unrelated screens.

## 14. Risks & open items

- **10.183 WIP reconciliation** (owner's; documented merge guide at the end — overlapping files: `PlannerService.java`, `PlanResults.tsx`, `Planner.tsx`, `i18n.ts`, `styles.css`; note the `onReplace → Promise<boolean>` signature).
- **localStorage limits** (not cross-device/account) — accepted.
- **adjust-move-in** is the largest new surface; honest no-op messaging is the key correctness risk.
- **12-language translations** authored carefully but native review recommended post-sprint.
- **Move-In results screenshots time out** in preview → rely on DOM measurements (`javascript_tool`) + targeted screenshots.

## 15. Final report will include

Branch + source commit, final commit list, files changed, audit summary, per-feature behavior, apartment-status calc, checklist persistence + limitation, verbatim Croatian copy, i18n coverage, back-compat notes, backend test results, frontend tsc/build/guard results, live smoke results, unresolved risks, browser-verification checklist/screenshots, 10.183 merge guide, and a readiness verdict — with **no merge/deploy**.
