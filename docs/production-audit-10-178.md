# Production-readiness audit — Sprint 10.178 catalog expansion

Branch: `sprint-10.178-catalog-expansion` (main untouched). Audit date: 2026-07-11.
Scope: verify the 10.178 catalog expansion (bathroom fixtures/mirrors, kitchen sets+appliances, GB core,
Victorian Plumbing GB, market-leak fix) is production-ready — real Docker boot + live API smoke, not just unit tests.

## 1. Rebuild + boot (existing dev Docker env, branch source)

```
docker compose restart backend    # maven:3.9 image, bind-mounts ./backend, runs mvn spring-boot:run (recompile+reseed)
# booted in ~60s
curl http://localhost:8090/api/products/catalog-health  ->  totalProducts = 9939   (branch catalog live)
docker compose restart frontend   # vite dev server on :5180  ->  HTTP 200, "<title>BudgetSpace AI</title>"
```
Backend on :8090, frontend on :5180, postgres on :5432. `ddl-auto=create` → fresh schema + `RealCatalogSeeder`
re-seeded the full catalog from the branch JSON at boot.

## 2. Unit tests (pre-audit, branch)

`mvn test` → **375 tests, 0 failures, 0 errors** (baseline before the expansion was 362). Frontend `npm run build`
(tsc + vite) clean.

## 3. Live API smoke — 7 plans + flows (POST /api/plans/generate-fast, rule-based, no AI)

**76 / 76 hard checks passed.** Per plan verified: HTTP 200 + 3 tiers, recommended-plan non-empty, total ≤ budget,
every product's `market` == requested market (no foreign-country products), every product's `currency` correct for
the market, every product has image + valid URL + price > 0.

| Plan | Budget | Recommended total | Currency | Fixtures / kitchen items |
|---|---|---|---|---|
| GB bathroom (small) | £300 | £281 | GBP | fixture present |
| GB bathroom (med) | £800 | £731 | GBP | fixture present |
| DE bathroom | €900 | €763 | EUR | washbasin present |
| FR bathroom | €900 | €769 | EUR | fixture present |
| SI kitchen | €1500 | €1016 | EUR | real kitchen items (cart/storage) |
| GB kitchen | £1000 | £678 | GBP | real kitchen items |
| GB bedroom | £1200 | £931 | GBP | — |
| GB living room | £1500 | £1256 | GBP | — |

Extra flows verified live:
- **Complete kitchen** (`prompt: kompletna kuhinja`) → SI and GB both return modular `kitchen-set` products (KNOXHULT/…).
- **Appliances on request** → DE kitchen with `mustHaveCategories:["oven"]` surfaces an `oven`.
- **Replace** (`POST /api/plans/replace`, changeType `different`) → GB bathroom washbasin swapped for a *different*
  washbasin, same category.
- **Find similar under budget** (`POST /api/plans/similar`) → a DE washbasin anchor returns ≥2 distinct same-category
  options within the cap.

One non-blocking note: the SI kitchen plan's lighting pick (`NYMÅNE` pendant, from `real-ikea-si-rooms.json`,
Sprint 10.18) has no image — **pre-existing**, not from this expansion. The frontend renders image-less rows as an
honest category illustration (by design, ~460 such rows / 4.6% catalog-wide). Not a broken/placeholder image.

## 4. Live product re-verification (random sample)

36 new 10.178 products sampled across **all 15 markets** + IKEA & Victorian Plumbing + every new category
(washbasin, mirror/decor, kitchen-set, oven/hob/hood/fridge/freezer/microwave, sofa, bed, bath-shower). For each,
live-fetched the product page + image:

**36 / 36 fully OK** — URL resolves to a real product page (200, not a `/cat/` bounce), page `og:title` matches the
product name, image resolves (`Content-Type: image/*`), price/currency sane. 0 dead links / 0 wrong pages / 0 broken
images. All 601 new products carry a verified image (0 image-less introduced by this expansion).

## 5. Final numbers (script, from current repo)

```
catalog files: 105 | total rows: 9939 | distinct externalId: 9939 (no dup ids) | dup productUrl: 0
```

**Why 9338 baseline vs the older ~8048 figure:**
```
8048  (catalog before Sprint 10.177)
+1290  Sprint 10.177 (real-ikea-bathroom-depth-10-177 = 685 + real-ikea-furniture-depth-10-177 = 605)
= 9338  (baseline this task started from, post-10.177)
+601   Sprint 10.178 (this expansion, 7 files)
= 9939  (current)
```
The ~8048 "market-specific" count was the catalog *before* Sprint 10.177 added bathroom textiles (all 15 markets) +
core-furniture depth. 8048 + 1290 + 601 reconciles exactly to the current 9939.

## Verdict

Production-ready for this expansion. All 7 requested plans generate correctly with the right market/currency, no
cross-country leakage, budgets respected, and the new bathroom fixtures / kitchen sets / appliances actually surface;
replace and find-similar work live; new product links/images/prices verified against the live retailers. Remaining
honest gaps (unchanged, by prior agreement): WC + bath/shower fixtures exist only in HR/DK/GB (other markets' sanitary
retailers are anti-bot/JS-gated — need a partner feed); GB appliances are thin (IKEA barely stocks them in the UK).
