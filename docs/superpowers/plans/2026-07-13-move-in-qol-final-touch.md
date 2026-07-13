# Move-In QoL Final Touch — Implementation Plan

> **For agentic workers:** Execute task-by-task. Steps use checkbox (`- [ ]`) syntax. This plan is executed **inline in the main session** (context is concentrated here: full backend+frontend audit, shared types, interconnected state). Each task ends with its own tests + a commit.

**Goal:** Add priorities, keep-room/product, whole-apartment adjustments, a status overview, and a shopping checklist to the existing "Cijeli stan" Move-In planner, plus swap the hero image to a full-bleed banner — extending existing seams, not rebuilding.

**Architecture:** Backend `PlannerService.generateMoveIn` + a new thin `adjustMoveIn` reuse the single-room engine per room; priorities feed the existing `allocateMoveIn`; keep/status/checklist ride optional back-compat DTO fields + `localStorage`. Frontend lives in `MoveInPlanner.tsx` (+ `Planner.tsx` wiring) with a pure-logic helper module.

**Tech Stack:** Java 21 / Spring Boot (backend, `mvn test`), React 18 + TypeScript + Vite (frontend, `tsc -b && vite build`), i18n via `i18n.ts` DICTIONARY + `messages/*.json` overlays. No new frameworks.

## Global Constraints

- Base `d4a8344` (= `main`). **Never** merge/reset/deploy `main`; no touching the owner 10.183 WIP (it lives only in the main checkout, not this worktree).
- Persistence = `localStorage` only. No DB schema change, no Flyway migration, no first-class apartment entity.
- No test framework added. i18n guard + logic checks are zero-dep `.mjs`; UI verified in-browser via DOM measurements.
- All new user copy hand-written, Croatian verbatim per spec §9; EN + 12 overlays natural. **Banned labels:** "Optimiziraj", "Sekundarna faza", "Naknadno", "Može kasnije", "Pametna alokacija", "AI optimizacija", any "AI-powered"/sparkle/robot chrome.
- Invariant: `grandTotal ≤ totalBudget` always. Preserve fixture subtype (bathtub↔shower) and market/currency in every swap.
- All DTO additions are **optional** fields with legacy constructors (old JSON → null/default; old frontend ignores new fields).
- Green baseline: **858 backend tests, 0 failures**; frontend `vite build` clean. Keep both green.
- Backend build: `$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; & "C:\Users\bpusic\maven-tmp\apache-maven-3.9.9\bin\mvn.cmd" -s "C:\Users\bpusic\.m2\settings-central.xml" -f "<worktree>\backend\pom.xml" test`. Frontend: `npm run build` in `<worktree>\frontend`.

---

## File structure

**Backend (`backend/src/main/java/ai/budgetspace/`)**
- Modify `dto/MoveInRequestDto.java` — add optional `Map<String,String> roomPriority`.
- Modify `dto/MoveInRoomDto.java` — add optional `List<String> missingEssential, niceToHave, unavailableInMarket`.
- Modify `dto/MoveInResponse.java` — add optional `boolean changed, String message` (for adjust).
- Create `dto/MoveInAdjustRequest.java` + `dto/AdjustRoomDto.java`.
- Modify `planner/PlannerService.java` — priority-aware `allocateMoveIn`; surface missing buckets; new `adjustMoveIn`; thread per-room `lockedProductIds`; new `niceToHaveMissing`.
- Modify `planner/PlanController.java` — `POST /api/plans/adjust-move-in`.
- Tests: `planner/MoveInAllocationTest.java`, `planner/PlannerServiceTest.java` (or a new `MoveInAdjustTest.java`), `dto` back-compat test.

**Frontend (`frontend/src/`)**
- Create `utils/moveInPlan.ts` — pure helpers (priority factors, status computation, checklist totals, retained/budget math, session (de)serialize + hydrate defaults).
- Create `scripts/check-i18n.mjs` + `scripts/check-move-in.mjs` (+ npm scripts).
- Modify `components/MoveInPlanner.tsx` — priorities, keep toggles, adjust area, status overview, checklist; localStorage session.
- Modify `components/Planner.tsx` — pass any needed props (market for retained-clear).
- Modify `components/PlannerHero.tsx` + `styles.css` — banner background; delete `kauc.png`.
- Modify `types/index.ts` — `RoomPriority`, optional response fields.
- Modify `api/client.ts` — send `roomPriority` on generate; new `adjustMoveInPlan`.
- Modify `i18n.ts` + `messages/{de,es,fr,it,nl,pt,sl,sk,sv,no,da,fi}.json`.

---

### Task 1: Data-model foundation + back-compat

**Files:** Modify `dto/MoveInRequestDto.java`, `dto/MoveInRoomDto.java`; Create `utils/moveInPlan.ts`; Modify `types/index.ts`; Test `dto/MoveInDtoBackCompatTest.java` (new), `scripts/check-move-in.mjs` (new).

**Interfaces produced:**
- `MoveInRequestDto(PlannerInputDto base, List<String> rooms, int totalBudget, Map<String,String> roomPriority)` + legacy `(base, rooms, totalBudget)` delegating with `roomPriority=Map.of()`; a `roomPriority()` accessor never null (normalize null→empty).
- `MoveInRoomDto(String roomType, int allocatedBudget, List<FurnishingPlanDto> plans, boolean partial, List<String> missingEssential, List<String> niceToHave, List<String> unavailableInMarket)` + legacy 4-arg delegating with empty lists.
- TS: `type RoomPriority = 'now' | 'soon' | 'later'`; `MoveInRoomPlan` gains optional `missingEssential?`, `niceToHave?`, `unavailableInMarket?: ProductCategory[]|string[]`.
- TS helper `moveInPlan.ts`: `hydrateSession(raw: unknown, market: string): MoveInSession` with safe defaults; `serializeSession(s): string`.

- [ ] **Step 1 (backend test first):** In `MoveInDtoBackCompatTest`, deserialize a legacy JSON `{"roomType":"bedroom","allocatedBudget":100,"plans":[],"partial":false}` into `MoveInRoomDto` via the injected Jackson `ObjectMapper`; assert `missingEssential()` etc. are empty (not null). Same for `MoveInRequestDto` legacy `{base,rooms,totalBudget}` → `roomPriority()` empty. Run → FAIL (fields absent).
- [ ] **Step 2:** Add the optional record components + legacy constructors + null-normalizing accessors. Run test → PASS.
- [ ] **Step 3 (frontend):** Add `RoomPriority` + optional response fields to `types/index.ts`. Create `utils/moveInPlan.ts` with `hydrateSession` (unknown → defaults: `priorities={}`, `retainedRooms=[]`, `purchasedIds=[]`, drop `results`/`purchased` if `session.market !== market`).
- [ ] **Step 4:** Create `scripts/check-move-in.mjs` asserting `hydrateSession(null,'HR')` yields defaults and `hydrateSession({market:'DE',results:[...]}, 'HR')` drops results. Transpile the TS helper with the bundled esbuild (`node_modules/esbuild`), run assertions. Run `node scripts/check-move-in.mjs` → PASS. Add `"check:movein": "node scripts/check-move-in.mjs"` to `package.json`.
- [ ] **Step 5:** `mvn test` (DTO tests green, 858+N) + `npm run build`. Commit: `feat(move-in): optional back-compat DTO fields + localStorage session helper`.

### Task 2: Room priorities (real allocation)

**Files:** Modify `planner/PlannerService.java`; Test `planner/MoveInAllocationTest.java`; Modify `components/MoveInPlanner.tsx`, `api/client.ts`, `i18n.ts`.

**Interfaces produced:**
- `static int[] allocateMoveIn(int total, List<String> rooms, double[] floors, double sumFloors, boolean infeasible, double[] priorityFactors)`; legacy 5-arg overload passes all-`1.0`.
- `private static double priorityFactor(String level)` → `now=1.5, soon=1.0, later=0.6, default 1.0`.
- API: `generateMoveInPlan(base, rooms, totalBudget, priorities?: Record<string,RoomPriority>)`.

- [ ] **Step 1:** In `MoveInAllocationTest`, add `priorityShiftsLeftoverToNow`: two equal-floor rooms, total well above floors, `now` vs `later` → assert the `now` room's allocation strictly exceeds the `later` room's. Run → FAIL (param missing).
- [ ] **Step 2:** Add `priorityFactor` + the 6-arg `allocateMoveIn`: feasible branch multiplies weights by factor; infeasible branch reserves floors greedily in `now→soon→later` order (tie-break weight desc, then input order). Keep 5-arg overload = all-`1.0`. Run → PASS.
- [ ] **Step 3:** Add `infeasibleReservesNowFirst`: floors sum > total, `now` room floor fully funded, `later` room under-funded → assert `now` alloc == its floor and `later` alloc < its floor. Run → PASS. Add `noPriorityMatchesLegacy` (6-arg all-`1.0` == 5-arg) → PASS.
- [ ] **Step 4:** Wire `generateMoveIn` to build `priorityFactors` from `request.roomPriority()`; pass to `allocateMoveIn` and `capAllocationsToCapacity`. `mvn test` → green.
- [ ] **Step 5 (frontend):** `MoveInPlanner`: `priorities` state (`Record<RoomType,RoomPriority>`, default `soon`), a "Što ti treba prvo?" segmented control per selected room (mobile-tappable, `aria-pressed`), persist in draft. Send via `generateMoveInPlan`. Add HR+EN keys (§9). `npm run build`.
- [ ] **Step 6:** Browser-verify (DOM): pick 3 rooms, set bedroom `now`, generate, confirm bedroom's allocated chip ≥ others at equal weight. Commit: `feat(move-in): room priorities drive budget allocation`.

### Task 3: Keep a room / product

**Files:** Modify `components/MoveInPlanner.tsx`, `components/Planner.tsx`, `utils/moveInPlan.ts`, `i18n.ts`, `styles.css`; Modify `planner/PlannerService.java` (thread per-room locked ids in `moveInRoomInput` only where the adjust/replace path supplies them); Test `scripts/check-move-in.mjs`.

**Interfaces produced:**
- `moveInPlan.ts`: `retainedTotal(results, retainedRooms, lockedByRoom): number`; `retainedExceedsBudget(results, retainedRooms, lockedByRoom, budget): boolean`; `clearForeignMarketRetained(results, market): {results, cleared}`.

- [ ] **Step 1:** In `check-move-in.mjs` add: retained sum = kept rooms' totals + kept products in non-kept rooms; `retainedExceedsBudget` true when that sum > budget; `clearForeignMarketRetained` drops locked ids whose product.market ≠ market. Run → FAIL.
- [ ] **Step 2:** Implement those pure helpers in `moveInPlan.ts`. Run → PASS.
- [ ] **Step 3 (UI):** Per-item "Zadrži ovaj proizvod" toggle (adds `product.id` to that room's `input.lockedProductIds`; a kept row shows a "Zadržano" chip and disables its swap/remove, mirroring single-room lock). Per-room header "Zadrži ovu sobu" toggle (`retainedRooms: Set<RoomType>`; kept room shows the state + hint). Copy per §9. Kept state persisted in session.
- [ ] **Step 4:** Guards: on budget change, if `retainedExceedsBudget` → show `moveIn.retainedExceedsBudget`, do not drop/overspend. On market change (from `Planner` market prop), run `clearForeignMarketRetained`, and if any cleared show `moveIn.retainedMarketCleared`.
- [ ] **Step 5:** `npm run build`; browser-verify keep/unlock toggles + budget-exceeds message. Commit: `feat(move-in): keep a room or product (retained state + guards)`.

### Task 4: Adjust the whole apartment

**Files:** Create `dto/MoveInAdjustRequest.java`, `dto/AdjustRoomDto.java`; Modify `dto/MoveInResponse.java`, `planner/PlannerService.java`, `planner/PlanController.java`, `api/client.ts`, `components/MoveInPlanner.tsx`, `i18n.ts`, `styles.css`; Test `planner/MoveInAdjustTest.java`.

**Interfaces produced:**
- `MoveInResponse(... , boolean changed, String message)` + legacy 5-arg (`changed=true, message=null`).
- `MoveInAdjustRequest(PlannerInputDto base, List<AdjustRoomDto> rooms, int totalBudget, String action, Integer targetTotal, Map<String,String> roomPriority)`; `AdjustRoomDto(String roomType, FurnishingPlanDto plan, boolean retained, List<String> lockedProductIds)`.
- `public MoveInResponse adjustMoveIn(MoveInAdjustRequest req)`.
- API: `adjustMoveInPlan(base, rooms, totalBudget, action, targetTotal, priorities): Promise<MoveInApiResponse>`.

- [ ] **Step 1:** `MoveInAdjustTest.reduceTotalPrefersMinimalChange`: build a 2-room apartment via `generateMoveIn`, then `adjustMoveIn` action `reduce-total` with `targetTotal` = 85% of current, one room `retained` → assert new grandTotal ≤ target, retained room byte-identical, ≥1 non-retained item cheaper, no fixture-subtype flip. Run → FAIL (method absent).
- [ ] **Step 2:** Implement `adjustMoveIn`: retained rooms pass through (subtract spend); non-retained rooms re-run `generateSeeded` with action-adjusted `moveInRoomInput` (per-room `lockedProductIds` pinned). `reduce-total`: re-allocate `targetTotal−retainedSpend` priority-aware; rely on `repairBudget`. Set `changed=false` + `message` when target < retained+floors. Run → PASS.
- [ ] **Step 3:** Add `fewerStoresConsolidatesWithinBudget` (re-run non-retained with `retailerMode=single`/dominant retailer; assert distinct-retailer count drops or `changed=false`+noop message; total ≤ budget) and `useRemainingUpgradesWithinCap` (remaining>0 → some non-retained item price rises, grandTotal ≤ budget, retained untouched) and `marketIsolationHolds` (all products stay in base.market). Implement the two other actions. Run → PASS.
- [ ] **Step 4:** Add controller `POST /api/plans/adjust-move-in`. `mvn test` green.
- [ ] **Step 5 (UI):** "Što želiš promijeniti?" area + hint + 3 buttons (reduce-total shows a target input prefilled to current total, max=budget). Call `adjustMoveInPlan`; splice returned rooms into `results` (keep retained rooms); show honest `message` when `changed=false`. Copy per §9. `npm run build`; browser-verify each action + a no-op message. Commit: `feat(move-in): adjust-apartment actions (reduce / fewer stores / use remaining)`.

### Task 5: Status overview + shopping checklist

**Files:** Modify `planner/PlannerService.java` (`niceToHaveMissing`, map buckets into `MoveInRoomDto` in `generateMoveIn` + `adjustMoveIn`); Modify `components/MoveInPlanner.tsx`, `utils/moveInPlan.ts`, `styles.css`, `i18n.ts`; Test `planner/PlannerServiceTest.java`, `scripts/check-move-in.mjs`.

**Interfaces produced:**
- `moveInPlan.ts`: `apartmentStatus(results, budget, retained*): {total, remaining, over, roomCount, retailerCount, coveredRooms, attentionRooms, missing:{moveIn[],niceToHave[],notFound[]}}`; `checklist(results): {byRetailer: Array<{retailer,count,total,items[]}>}`; `checklistTotals(results, purchasedIds): {bought, remaining}`.

- [ ] **Step 1 (backend):** `PlannerServiceTest.moveInRoomCarriesMissingBuckets`: a market/room known to lack a required category → assert the room's `MoveInRoomDto.missingEssential` non-empty and `unavailableInMarket` reflects an explicit unmet must-have. Run → FAIL.
- [ ] **Step 2:** Add `niceToHaveMissing(input, planItems)` (desired optional categories absent but available in `marketCatalog`); map `missingImportantCategories`/`niceToHave`/`unavailableRequestedCategories` into `MoveInRoomDto` in both `generateMoveIn` and `adjustMoveIn`. Run → PASS.
- [ ] **Step 3 (frontend logic):** In `check-move-in.mjs`: `apartmentStatus` totals/covered/attention correct on a fixture; `checklistTotals` sums bought vs unchecked by `product.id`. Implement `apartmentStatus`/`checklist`/`checklistTotals` in `moveInPlan.ts`. Run → PASS.
- [ ] **Step 4 (status UI):** Extend the top card: total/`Ostaje u budžetu`/rooms/`Kupnja u N trgovina`/`Još treba riješiti`/`Ove sobe su pokrivene`, plus a "Pokaži što još nedostaje" toggle revealing the three groups (`Treba za useljenje`/`Dobro je dodati`/`Nije pronađeno za tvoje tržište`). Real budget bar only — no % ready score.
- [ ] **Step 5 (checklist UI):** "Popis za kupnju" grouped by retailer (header `RETAILER — N proizvoda — {total}`; rows: checkbox + name + room + price + open link); `Kupljeno`/`Još za kupiti` totals. `purchasedIds` state persisted; marking bought never removes the item. Copy per §9.
- [ ] **Step 6:** `mvn test` + `npm run build`; browser-verify status groups + checkbox persistence across a reload. Commit: `feat(move-in): apartment status overview + shopping checklist`.

### Task 6: Banner (kauc.png → banner.png full-bleed)

**Files:** Modify `components/PlannerHero.tsx`, `styles.css`; Delete `kauc.png`.

- [ ] **Step 1:** In `PlannerHero.tsx` remove `import kaucImg`, remove the whole `.planner-hero-visual` block; add `import bannerImg from '../banner.png'`; set `style={{ backgroundImage: \`url(${bannerImg})\` }}` on `.planner-hero`.
- [ ] **Step 2:** In `styles.css` `.planner-hero`: `background-size: cover`; balanced `background-position` (text over the open center); constrain content max-width so text sits in the light middle; restrained warm overlay via a local `linear-gradient` layered under `bannerImg` (no dark overlay/card/heavy blur). Mobile: responsive `min-height` + adjusted `background-position` so the sofa isn't behind the text; no horizontal scroll; readable padding; buttons fully visible.
- [ ] **Step 3:** `git rm frontend/src/kauc.png` (grep-confirmed only PlannerHero referenced it).
- [ ] **Step 4:** `npm run build`; confirm `banner.png` hashed into `dist/assets`. Browser-verify desktop (1280) + mobile (375): legible text, tappable buttons, no h-scroll. Commit: `feat(hero): full-bleed banner background (replace kauc.png)`.

### Task 7: i18n all locales + guard + accessibility

**Files:** Modify `i18n.ts`, `messages/{de,es,fr,it,nl,pt,sl,sk,sv,no,da,fi}.json`; Create `scripts/check-i18n.mjs`; Modify `package.json`.

- [ ] **Step 1:** Create `scripts/check-i18n.mjs` (zero-dep): regex-extract DICTIONARY keys from `i18n.ts`; for each overlay report keys missing / orphan; exit 1 on any gap for the sprint's new keys. Add `"check:i18n": "node scripts/check-i18n.mjs"`.
- [ ] **Step 2:** Run guard → it FAILS listing the new keys missing from overlays.
- [ ] **Step 3:** Add natural translations of every new key to all 12 overlays (hand-written, not literal MT; HR store-count plural handled in code). Re-run guard → PASS.
- [ ] **Step 4:** Accessibility pass: all new toggles/buttons have `aria-label`/`aria-pressed`, visible focus, checklist checkboxes labelled, status uses `role="status"` where live. `npm run build`. Commit: `i18n(move-in): translate new keys to all locales + completeness guard`.

### Task 8: Regression + fixes

**Files:** as needed for fixes.

- [ ] **Step 1:** Full `mvn test` → expect 858 + new, 0 failures. Fix any regression.
- [ ] **Step 2:** `npm run build` + `node scripts/check-i18n.mjs` + `node scripts/check-move-in.mjs` → all green.
- [ ] **Step 3:** Live boot smoke: recreate/boot backend, `generate-move-in` + `adjust-move-in` API smokes (3-room limited budget bedroom `now`; retain living room + reduce-total; retain a sofa + fewer-stores; use-remaining; NL bathroom stays honest). Record results.
- [ ] **Step 4:** Browser verification checklist (desktop + mobile): priorities, keep toggles, adjust actions, status groups, checklist persistence, single↔apartment no leakage, old-draft hydrate. Screenshot where it renders; DOM-measure where screenshots time out.
- [ ] **Step 5:** Final commit if fixes were needed: `test(move-in): regression fixes + verification`. Assemble the final report + 10.183 merge guide.

---

## Self-review

**Spec coverage:** F1 priorities → T2; F2 keep → T3; F3 adjust → T4; F4 status → T5; F5 checklist → T5; banner → T6; i18n+guard → T1(guard scaffold)/T7; persistence → T1/T3/T5 (localStorage session); back-compat → T1; tests → each task + T8; copy → §9 embedded in T2/T3/T4/T5. All covered.

**Placeholder scan:** No "TBD/handle edge cases/similar-to". Each step names the concrete file, signature, test assertion, or command.

**Type consistency:** `allocateMoveIn` 6-arg + 5-arg overload used consistently (T2). `MoveInRoomDto` 7-field + legacy 4-arg (T1) consumed in T5. `MoveInResponse` +changed/message (T4). `moveInPlan.ts` helper names (`hydrateSession`, `retainedExceedsBudget`, `clearForeignMarketRetained`, `apartmentStatus`, `checklist`, `checklistTotals`) defined in T1/T3/T5 and reused consistently.
