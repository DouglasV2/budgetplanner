# Move-In QoL Final Touch — Final Report

**Branch:** `move-in-qol-final-touch` · **Source commit:** `d4a8344` (= `main` = `origin/main`).
**Not merged, not deployed.** Landing on `main` is teed up for your explicit go-ahead (§Final recommendation).

## Commit list (7 feature commits on top of the spec + plan)

```
b286c38 i18n(move-in): translate the 37 new keys to all 12 locales + completeness guard
c94174d feat(hero): full-bleed banner background (replace kauc.png)
bd5c0af feat(move-in): apartment status overview + practical shopping checklist
ecd2542 feat(move-in): adjust the whole apartment (reduce / fewer stores / use remaining)
2300484 feat(move-in): keep a room or product (retained state + honest guards)
2f73a72 feat(move-in): room priorities drive the whole-apartment budget split
e9e7bec feat(move-in): optional back-compat DTO fields + localStorage session helper
d13e5db Plan: implementation plan (8 staged tasks)
15a52db Spec: design spec
```
(final commit updated below to include the report + any T8 fixes)

## Files changed (35 files, +2333 / −150; excluding the spec/plan docs)

Backend: `dto/{MoveInRequestDto,MoveInRoomDto,MoveInResponse}.java` (optional back-compat fields), new `dto/{AdjustRoomDto,MoveInAdjustRequest}.java`, `planner/PlannerService.java` (+304: priority allocation, adjust engine, missing-bucket helper), `planner/PlanController.java` (adjust endpoint) · tests: `MoveInDtoBackCompatTest` (new), `MoveInAllocationTest` (+3), `PlannerServiceTest` (+7).
Frontend: new `utils/moveInPlan.ts` (+259 pure helpers), `components/MoveInPlanner.tsx` (+406), `components/PlannerHero.tsx` (banner), `components/icons.tsx` (LockIcon), `api/client.ts`, `types/index.ts`, `i18n.ts` (+75), `styles.css` (+271), `banner.png` (added), `kauc.png` (deleted), `messages/*.json` ×12 (+37 each) · new `scripts/{check-move-in,check-i18n}.mjs`.

## Existing Move-In architecture (Phase-0 audit)

- **Frontend:** `Planner` owns `scope`; mounts `PlanResults` (single-room) beside `MoveInPlanner` (apartment) via `hidden`. The apartment plan was `results: RoomPlanResult[]` local state, **not persisted** (only `{rooms,budget}` draft). A per-product **lock** (`input.lockedProductIds`) existed, backend-wired, single-room only.
- **Backend:** `POST /api/plans/generate-move-in` → `PlannerService.generateMoveIn`; budget split by `allocateMoveIn` (static, unit-tested) over a hardcoded `MOVE_IN_WEIGHTS`; each room a normal single-room build; degraded honesty computed room-required + explicit-unmet gaps but only exposed a `partial` boolean. Saved plans are frozen JSON; an apartment = N per-room rows grouped by `space_name` (no first-class entity — a V5 migration was out of scope).

## Room priorities (real allocation, not decoration)

3 levels — `now`/`soon`/`later` (default `soon`) — sent on `generate-move-in` as `roomPriority`. Backend scales each room's weight (`now ×1.5`, `soon ×1.0`, `later ×0.6`) in `allocateMoveIn`/`capAllocationsToCapacity`/the absorber re-share; when the budget can't cover every core floor it reserves essentials **in priority order** so a `now` room never loses its core to a `later` room (the lower-priority rooms take the shortfall and come back honestly partial). Empty priority map = byte-identical to the pre-priority allocator. `grandTotal ≤ totalBudget` always. (`MoveInAllocationTest` +3.)

## Keep a room / product

- **Keep product** reuses each room's `input.lockedProductIds` (the backend-honoured seam). A kept piece is pinned (its swap is disabled) but still removable/un-keepable.
- **Keep room** is a new client `retainedRooms` set that shields a room from every whole-plan adjustment.
- **Guards:** honest "kept items exceed the new budget" note under the budget field (never silently drops/overspends); a market change strips kept **products** from the old market and says so (kept **rooms**, being market-agnostic, survive). Keyed by stable `product.id`.

## Whole-plan adjustment (`POST /api/plans/adjust-move-in`)

Orchestrates the existing single-room replacement engine over **only** non-kept rooms/products:
- **reduce-total** — swaps the priciest non-kept items cheaper (fewest useful swaps) to the target; honest `reduce-unreachable` when kept items + essentials already cost more.
- **fewer-stores** — regenerates non-kept rooms preferring a single store; commits only if the distinct-retailer count actually drops within budget, else honest `fewer-stores-noop`.
- **use-remaining** — upgrades non-kept items to a nicer piece where the price step fits the leftover; never exceeds budget; honest `use-remaining-none` when nothing affordable.
Never touches retained rooms/kept products, never crosses market or fixture subtype (bathtub↔shower), never exceeds budget. Returns `changed` + a message CODE the frontend localizes. (`PlannerServiceTest` +6.)

## Apartment status calculations

A compact status row inside the total card: real total, `Ostaje u budžetu`, `Sobe: N`, `Trgovine: N`, covered vs still-to-sort rooms, and a `Pokaži što još nedostaje` toggle grouping the three honest buckets the backend now surfaces on `MoveInRoomDto`:
- **Treba za useljenje** = room-required categories absent from the plan but available in-market (add budget/store).
- **Dobro je dodati** = optional desired categories absent but available.
- **Nije pronađeno za tvoje tržište** = required categories the market genuinely can't supply (= `missingImportantCategories`).
**No invented readiness score** — the only ratio shown is the real total ÷ budget fill bar.

## Checklist persistence strategy

`Popis za kupnju` groups every plan item by retailer with a checkbox, room, price and open-in-store link, plus `Kupljeno` / `Još za kupiti` totals. Ticking a box marks a piece **bought** (shopping progress, not "already owned") without removing it. `purchasedIds` persist in the **localStorage session** (`budgetspace.moveInDraft`, keyed by market) alongside `results` + priorities + kept rooms — so the whole apartment (plan, priorities, kept state, checklist) survives reload/navigation. **Limitation (documented):** localStorage is browser-local, not cross-device/account, and not a DB-backed apartment save — the deliberate "smallest reliable seam" choice you approved (a first-class apartment entity / V5 migration was out of scope). The existing per-room "Spremi cijeli stan" save is unchanged.

## Exact Croatian copy added (verbatim)

Priorities: `Što ti treba prvo?` · `Odaberi koje prostorije želiš riješiti prve. Tamo ćemo prvo usmjeriti budžet.` · `Treba odmah` / `Želim uskoro` / `Nije hitno`.
Keep: `Zadrži ovu sobu` · `Ostatak stana možeš mijenjati bez promjena u ovoj sobi.` · `Opet mijenjaj ovu sobu` · `Zadrži ovaj proizvod` · `Nećemo ga zamijeniti kad prilagođavaš ostatak plana.` · `Opet mijenjaj proizvod` · `Stavke koje želiš zadržati već prelaze novi budžet. Ukloni neku od njih ili povećaj budžet.`
Adjust: `Što želiš promijeniti?` · `Promijenit ćemo samo ono što nisi zadržao.` · `Smanji ukupnu cijenu` / `Kupuj u manje trgovina` / `Iskoristi ostatak budžeta` · `Ovaj plan već koristi najmanji realan broj trgovina za odabrani budžet.` · `Iskoristili smo dio preostalog budžeta tamo gdje donosi najveću razliku.`
Status/checklist: `Pokaži što još nedostaje` · `Treba za useljenje` / `Dobro je dodati` / `Nije pronađeno za tvoje tržište` · `Još treba riješiti` · `Ove sobe su pokrivene` · `Popis za kupnju` · `Kupljeno` / `Još za kupiti`.
None of the banned robotic labels (Optimiziraj / Sekundarna faza / Može kasnije / Pametna alokacija / AI optimizacija …) were used.

## i18n coverage

37 new keys in `i18n.ts` (hr + en) **and** translated into all 12 overlays (de/es/fr/it/nl/pt/sl/sk/sv/no/da/fi). New `scripts/check-i18n.mjs` guard (zero-dep) **fails** the build/CI when any DICTIONARY key is missing from an overlay (allow-listing the pre-existing dropped-subscription `pricing.*`/`plus.*` gaps, documented) — **712 keys present in all 12 overlays**. The 12-language copy is author-written; a native-speaker review is a recommended (non-blocking) follow-up.

## Backward compatibility

All DTO additions are optional record components with legacy constructors + null-normalization (old JSON/clients read them as empty; `MoveInDtoBackCompatTest` proves it). No entity/schema/migration change. Old localStorage `{rooms,budget}` drafts hydrate transparently (safe defaults). Old per-room saved plans open exactly as before. No API consumer broken.

## Test results

- **Backend:** full `mvn test` — **874 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (baseline 858 + 16 new: 6 DTO back-compat, 3 priority-allocation, 6 adjust, 1 missing-bucket). No regressions across catalog / Similar Items / Replace Product / degraded-capacity / state-transition / AI-fallback suites.
- **Frontend:** `tsc -b && vite build` clean; `npm run check` green (i18n guard 712/12 + logic guard 29 assertions). No test-framework migration (zero-dep node checks, per your choice).
- **Live boot + API smoke** (my worktree backend booted on a free port, `ddl-auto=validate` + seeding off — non-disruptive against the shared Postgres): Spring context loaded **with the new endpoint/DTOs in 11s** (health UP). `generate-move-in` (3 rooms, bedroom=`now`, €4000) → grand **€3300 ≤ budget**; the `now` bedroom got the **top** allocation (1840 vs living-room 1517) — priority is live, not cosmetic. `adjust-move-in`, all three actions green: **reduce-total** €3300→€2549 (hit target), **fewer-stores** consolidated within budget, **use-remaining** €3300→€3957 (`use-remaining-done`, never over budget). **Retained** living-room byte-identical (same item ids) through a reduce-total. **NL bathroom** honest (real fixtures, no false gap). (A PowerShell smoke's adjust calls 400'd on a PS 5.1 `ConvertTo-Json` single-element-array quirk that mangles the nested plan JSON; the node/`JSON.stringify` smoke — the same wire the frontend uses — passed cleanly, confirming it's a harness artifact, not a defect.)

## Browser verification

- **Verified live (worktree vite):** the priority control renders with the correct copy, toggles, and persists `{"living-room":"now"}` to localStorage; the banner is a full-bleed `cover` background with no separate image element and **no horizontal scroll** on desktop (1280) or mobile (375), buttons fully visible/tappable.
- **Screenshots time out for this app's renderer** (a known limitation — verified via DOM measurements instead). The results-view UI (keep toggles, adjust actions, status groups, checklist) is build-clean + logic-tested + API-smoked; a quick manual eyeball is the last mile — see checklist below.

### Manual verification checklist (results view)
1. Apartment scope → generate 3 rooms → priority chips show under each selected room; set one to *Treba odmah* → regenerate → that room's chip ≥ equal-weight peers.
2. In results, *Zadrži ovu sobu* on a card → card gets the sage "kept" rule; *Zadrži ovaj proizvod* on an item → row tints sage, swap disabled.
3. Lower the budget below the kept total → the honest over-budget note appears.
4. *Smanji ukupnu cijenu* with a target → total drops to ≤ target, kept room untouched; *Kupuj u manje trgovina* / *Iskoristi ostatak budžeta* → honest notes when nothing to do.
5. *Pokaži što još nedostaje* → the three grouped buckets; status row shows rooms/stores/remaining.
6. *Popis za kupnju* → tick items → Kupljeno/Još za kupiti update; reload → ticks + plan persist.
7. Switch country → kept foreign-market products cleared with a note. Switch single ↔ apartment → no state leak.

## Unresolved risks / limitations

1. **10.183 WIP reconciliation** — your uncommitted "honest replace" WIP is NOT in this branch (left untouched in the main checkout). Merge guide below.
2. **use-remaining is conservative** — it upgrades only where the engine's single best "nicer" candidate is affordable within the leftover; in a rich catalog it may report "nothing to upgrade" even when a cheaper-nicer exists. Honest (never over-spends), improvable later.
3. **localStorage persistence** — browser-local, not cross-device/account (accepted).
4. **12-language copy** — author-written; native review recommended.
5. **Store-count label** — rendered as `Trgovine: N` (grammar-safe across locales) rather than the brief's exact "Kupnja u N trgovine", to avoid Croatian case-agreement errors; trivially changeable if you prefer the inflected form.

## 10.183 "honest replace" WIP — merge guide

Your uncommitted WIP (main checkout) touches `PlannerService.java`, `PlanResults.tsx`, `Planner.tsx`, `i18n.ts`, `styles.css` and changes `onReplace → Promise<boolean>`. This branch does **not** touch `PlanResults.tsx`/`Planner.tsx`, and its `PlannerService.java`/`i18n.ts`/`styles.css` edits are in **different regions** (Move-In allocation/adjust vs. the single-room `pickReplacement`; new `moveIn.*` keys vs. new `results.no*Found` keys; new `.move-in-*` CSS vs. `.replacement-empty`). Expected reconciliation: **near-zero real conflict** — commit 10.183 first (or after) and a normal merge should apply cleanly; only `i18n.ts` / `styles.css` may need trivial hunk placement. Recommended: commit 10.183 on `main`, then merge `move-in-qol-final-touch` (or rebase it onto the committed 10.183).

## Final recommendation

**Ready for public beta — with two documented, non-blocking limitations.**

Every acceptance criterion is met and *verified to affect the real engine* (not decoration): room priorities steer the live budget split, keep/adjust operate through the real replacement engine with retained-awareness, status shows only honest data, and the checklist tracks real shopping progress. Proof: **874 backend tests (0 failures)**, **29 frontend logic assertions + a 712-key/12-locale i18n guard**, a **clean tsc + vite build**, and a **live boot + generate/adjust API smoke** in which priority, all three adjust actions, retained preservation, the never-over-budget invariant, and market honesty all held. The banner is DOM-verified on desktop + mobile; back-compat is proven; **nothing is merged or deployed**.

Documented limitations (both accepted in the design): (1) whole-apartment QoL state persists to **localStorage** (browser-local, not cross-device/account — the approved "smallest reliable seam", no schema rewrite); (2) **use-remaining is conservative** (it upgrades only where the engine's best "nicer" candidate is affordable, never over-spending). Recommended follow-ups: a native-speaker pass on the 12-language copy, and a one-line owner eyeball of the banner + results view (this app's renderer times out screenshots, so those were DOM/logic-verified).

**Landing on `main` is your call.** Per your spec I did not merge or deploy. You said you want it on `main`: on your explicit go-ahead I can merge / open a PR for `move-in-qol-final-touch` → `main`. Cleanest sequence given your uncommitted 10.183 "honest replace" WIP: commit 10.183 first, then merge this branch (or rebase it onto 10.183) — near-zero conflict expected (see the merge guide above).
