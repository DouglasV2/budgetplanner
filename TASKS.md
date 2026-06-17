# BudgetSpace AI — Tasks

Living backlog + done log. Pair with `MEMORY.md` and `ARCHITECTURE.md`.

## Road to production (sequenced — added 2026-06-16)

Goal path: **controlled HR beta first**, then full multi-market launch. Catalog + tests + architecture
are solid; the work to production is data freshness, AI enablement, market/UX exposure and prod hardening
— not core code. Do the 5 steps in order; check off as done.

### Next 5 steps (owner-confirmed order 2026-06-16; deploy target = Railway)

**LLM/OpenAI is intentionally deferred** until the HR catalog is maxed + re-verified — the owner does not
want to spend OpenAI keys testing until HR is as complete as possible. It runs *after* these 5 (it stays
the next phase: enable `BUDGETSPACE_AI_ENABLED=true` + verify `AiUsageTracker` caps + rule-based fallback;
needs `OPENAI_API_KEY`, backend env only).

1. **[x] Planner verified-only gate (catalog integrity end-to-end).** ✅ **Done 2026-06-16.**
   `PlannerService.marketCatalog` now filters on the new `CatalogSourcePolicy.isPlannerEligible` (=
   `isProductionVerified` minus the staleness check), so plans are built only from sourced, in-stock,
   non-`needs-review` products and **never** from `data.sql` sample rows or a blocked retailer that wasn't
   fed. Stale rows still enter (with the "provjeri u trgovini" note) so an aging catalog never silently
   empties; uncovered rooms (e.g. `home-gym`) yield an honest empty/partial plan. Added test
   `sampleProductWithoutSourceReferenceIsExcludedFromPlan`; recalibrated planner test helpers. 129 tests, 0 failures.
2. **[x] Maximize the HR catalog (all reachable shops, all rooms).** ✅ **Done 2026-06-17.** Gap-driven:
   measured HR coverage by room×category, then web-verified **+53** rows (`real-hr-max-10-22.json`) filling
   every thin cell — dining-room storage 0→4 / lighting 1→4 / decor 2→5; home-office storage 2→6 / decor 0→2 /
   rug 0→1; kitchen storage 1→5 / decor 0→2; hallway lighting 2→6; bathroom decor 2→7 — plus non-IKEA breadth
   (Emmezeta/Harvey Norman/Namjestaj.hr: corner sofas, beds, wardrobes, sideboards, dining sets across budget→
   premium). HR sourced rows 237→**290**; every planner-flow cell now ≥1. No 403-bypass. `HrMaxCatalogRuntimeTest`
   (0 import errors); programmatic dedup dropped 5 already-present URLs. Backend **130 tests, 0 failures**.
3. **[x] HR data re-verification before launch.** ✅ **Done 2026-06-17 (Sprints 10.25 + 10.27).** 10.25 fixed
   the URLs (16 dead → `needs-review`, 18 drifted → refreshed + imaged). 10.27 re-verified price+stock on every
   one of the 301 `partial` HR rows on its live page (deterministic raw-HTTP: JSON-LD / JYSK `priceAmount` /
   displayed €): **279 confirmed, 22 price-updated** (to the verified current regular price — HUGO precedent,
   big swings spot-checked), 0 newly dead. All got `lastCheckedAt=2026-06-17`; imaged rows flipped
   `partial → complete`. **HR now: 287 complete / 14 partial (Harvey Norman, no images) / 16 needs-review** →
   launch-ready. Cadence: re-run before launch + on the `isStale` window; `CatalogHealthService` reports it.
   Sub-parts: dedupe #10b ✅ (10.23); dead/drift URLs ✅ (10.25,
   [docs/hr-url-review-10-24.md](docs/hr-url-review-10-24.md)); price/stock re-verify ✅ (10.27).
4. **[x] Product images — fetch every real image we can.** ✅ **Done 2026-06-17 (Sprint 10.24).** Added the
   `imageVerified` field end to end (Product + DTOs + import + frontend gate; plumbing committed first) and
   web-verified **236 / 270** reachable HR product images by deterministically reading each product page's
   `og:image` (raw HTTP, no model → no fabrication), cross-checking product identity (IKEA slug / Emmezeta id /
   Namjestaj tokens; JYSK host), and confirming every image URL resolves (200 + image/*). IKEA normalised to a
   good display size (`?f=xl`). The UI now shows the real photo only when `imageVerified`, else keeps the
   placeholder. **Harvey Norman skipped** (its pages serve a wrong `og:image`). The 34 not imaged are dead
   /drifted URLs → step 3 (see [docs/hr-url-review-10-24.md](docs/hr-url-review-10-24.md)). *Done.*
5. **[ ] Security review + deploy to Railway + go-live checklist.** ⏸️ **DEFERRED by owner (2026-06-17) until
   the EU catalog is filled** — we want to test across all markets (HR/SI/AT/DE/IT/FI) safely *before* deploy,
   so don't start Railway/legal until the owner says so. Then: security review (keys backend-only, CORS,
   admin-endpoint guard active in prod, no secrets in logs/`.env.example`, input validation); Railway deploy
   (managed Postgres, env-based config for keys/feeds, **switch DB off `ddl-auto=create`** → Flyway/Liquibase
   migrations + backups, HTTPS, build config); Legal/GDPR + affiliate/sponsored disclosure copy. *Done when:*
   a controlled beta can go live on Railway safely.

> ⚠️ **Hard prod blocker (for when step 5 resumes):** `spring.jpa.hibernate.ddl-auto=create` rebuilds (wipes)
> the DB on every startup — fine for dev, fatal on Railway. Move to validate + versioned migrations before deploy.
> **OpenAI LLM is also DEFERRED by owner (2026-06-17) until the EU catalog is filled** — keys cost money to test,
> and we want a complete multi-market catalog first so prompts can be tested across countries without "no
> products" runs. Enable it (`BUDGETSPACE_AI_ENABLED=true` + verify `AiUsageTracker` caps + rule-based fallback;
> `OPENAI_API_KEY` backend env only) only after EU is filled and the owner gives the go-ahead.

### Further steps (post-HR-beta → full multi-market production)

- **[x] Expose + localize EU markets** ✅ **Done 2026-06-17 (Sprint 10.28).** Flipped `available:true` for
  SI/AT/DE/IT/FI in `markets.ts` (all six EUR markets now in the picker) and **fully localised the UI to English
  for every non-HR market** (HR stays Croatian): ~270 strings moved into the `i18n.ts` dictionary (hr+en) with
  `{param}` interpolation; the two large surfaces (`PlanResults`, `PlannerForm`) localised via subagents, the
  rest by hand. Backend-directed strings stay HR on purpose (quick-action prompt suffixes the rule-based parser
  reads; the Croatian `plan.name` tier values the UI maps to display keys). Per-market EUR currency formatting
  already worked. Frontend build clean; 0 missing keys. **Next: per-language localisation (DE/IT/SL/FI)** is a
  later step — English is the common language for now.
- **Full-catalog re-verification** near launch (all 6 markets, ~665 rows) — same freshness rule as step 3.
- **First real `RetailerFeed`** (Decathlon/Pevex/Lesnina) → unlocks `home-gym` and retires the last sample
  dependency (`ai.budgetspace.feed` seam already exists; never scrape).
- **Marketplace Phase 2 + 3**: a `MarketplaceFeed` over a compliant Njuškalo/FB API/export (rows →
  `sourceType=marketplace-listing`, each run through `MarketplaceListingFilter`), then the separate "Rabljeno"
  UI section + buyer-beware copy; used items stay out of the new-retail plan total.
- **Verified product images pipeline** (image-verification field; only show real images when verified, else
  keep the labelled placeholder).
- **Monetization live**: affiliate-click analytics + discreet sponsored labelling (never displaces best organic).
- **Non-EUR markets** (PL/CZ/HU/RO/SE/DK) once the UI handles their currency correctly.
- **Scale/perf**: load test, query/caching review, CDN for assets.

## Recently done

### Sprint 10.29 — EU depth: fill the IT + FI dining-room gap (current)
- Measured EU coverage by market×room: SI/AT/DE solid; **IT (51 rows, IKEA-only) and FI (65) had
  `dining-room=0`** — their dining-room plans were empty. Filled it with verified IKEA dining tables + chairs
  ported via the global article-number trick to `/it/it/` and `/fi/fi/` (the number resolves regardless of
  slug language): **9 rows** (IT 5: NORDEN, DANDERYD, ODGER×2, ROSENTORP; FI 4 — one ODGER redirected to a
  category in FI and was correctly dropped). Each row's localised name + **per-market EUR price** (DANDERYD
  139 IT / 149 FI; ODGER 60 IT / 99 FI — verified, not copied) + a **verified `og:image`** confirmed on
  ikea.com; spot-checked the NORDEN photo. `dataQuality=complete` (name+price+url+verified image, fresh).
  `real-eu-dining-10-29.json` + `EuDiningCatalogRuntimeTest`; backend **136 tests, 0 failures**.
- **Remaining EU depth (follow-up):** IT/FI are still IKEA-only and thin on bedroom/home-office; SI/AT/DE are
  reasonably covered. More breadth per market = the same number-trick / web-verify rule, scoped per owner.

### Sprint 10.28 — European app: expose + localise EU markets (EN for non-HR)
- **Exposed all six EUR markets** (`markets.ts` `available:true` for SI/AT/DE/IT/FI, HR already on) so the
  country picker offers the whole EUR region — the app is European, not HR-only. Each already had a verified
  EUR catalog (IKEA/JYSK from sprints 10.13–10.20), so plans render immediately.
- **Full English localisation for non-HR markets** (HR unchanged): extended the `i18n.ts` dictionary from ~13
  to ~340 keys (hr+en), added `{param}` interpolation to `translate()`/`t()`, and replaced ~270 hardcoded
  Croatian UI strings across Hero/HowItWorks/StatsStrip/Footer/Monetization/Planner/SavedPlansInbox/PlannerForm/
  PlanResults with `t('key')`. The two big files (PlanResults ~130 strings, PlannerForm ~120) were localised by
  parallel subagents returning strict key→{hr,en} JSON that I merged + build-verified.
- **Deliberately left HR** (not display): the quick-action prompt suffixes appended for the rule-based backend
  parser, and the Croatian `plan.name` tier values (`Najbolji izbor`/`Najjeftinije`/`Ljepša verzija`) the UI
  maps to display keys. The example prompt is seeded per-market language (EN version is city-less so it doesn't
  trip market auto-detection).
- Frontend build clean (tsc + vite); cross-checked **0 `t()` keys missing** from the dictionary. Per-language
  localisation (DE/IT/SL/FI) is the next UI step; English is the common language for now.

### Sprint 10.27 — full HR price/stock re-verification (closes road-to-production step 3)
- Re-verified **all 301 `partial` HR rows** on their live product pages — deterministic raw-HTTP (no model):
  JSON-LD `price` / JYSK `priceAmount` (the regular price, *not* the time-limited promo) / displayed €,
  plus a redirect→category dead-check and a non-IKEA schema.org OutOfStock check (IKEA stock is JS-lazy-loaded,
  so its static "OutOfStock" is a template artefact — ignored).
- **279 confirmed** (stored price still on the page) → `lastCheckedAt=2026-06-17`, flipped `partial→complete`
  where the row has a verified image. **22 price-updated** to the verified current price (e.g. JYSK ANDRUP
  regular 550→399, IKEA LACK 29.99→39.99, KALLAX 59.99→69.99, Emmezeta EVORA 1079.99→1349.99); recomputed
  `priceTier`; spot-checked every big swing on the live page (JYSK `priceAmount`=regular, IKEA SKU price). The
  initial run flagged 32 JYSK "drifts" that were actually a site-wide promo — adding `priceAmount` (regular)
  resolved them (HUGO precedent: keep the regular price, never the promo). **0 newly dead.**
- HR catalog now **287 complete / 14 partial (Harvey Norman — no images) / 16 needs-review (dead, from 10.25)**
  → **launch-ready**. `CatalogHealthService` already reports stale + dataQuality counts (re-check cadence).
- Backend **135 tests, 0 failures**; zero-churn line-edits (416/416). No fabrication, no 403-bypass.

### Sprint 10.26 — HR catalog breadth: more options per anchor category
- Owner asked for more HR furniture; new retailers are all JS-priced/403 (10.24 probe), so added **breadth
  from the verified retailers** instead. Filled real anchor gaps — **IKEA HR had ZERO beds/mattresses/
  wardrobes/nightstands** (only JYSK/Emmezeta did) — plus more desks/office-chairs/sofas/coffee-tables/
  TV-units, and JYSK + Emmezeta beds/wardrobes/dining/dressers. **+35 web-verified rows**
  (`real-hr-breadth-10-26.json`); catalog **710 → 745**, HR verified images **252 → 287**.
- Method: 3 sonnet subagents discovered + verified candidates (name/price/URL/tags) avoiding existing SKUs;
  then a **deterministic verification pass I ran**: fetch each URL (drop if it 301s to a category — caught 2
  drifted JYSK URLs), confirm `og:image` identity + that it resolves, and **take the price from JSON-LD for
  IKEA/JYSK (authoritative — every candidate price matched, 0 corrections)**; Emmezeta prices spot-checked
  on live pages (Ottowa 449.99, Bergen 898.99 ✓). Recomputed `priceTier`, normalised tags, deduped vs the
  committed catalog (0 dup URLs/ids). Spot-checked images visually (KLEPPSTAD bed, Ottowa sofa — correct).
- Every breadth row is planner-eligible **and** carries a verified image. Price spread budget 13 / standard
  16 / premium 6. `HrBreadthCatalogRuntimeTest`; backend **134 tests, 0 failures**. No fabrication, no 403-bypass.
- **Planner selection scales** (owner's concern): `PlannerService` scores each candidate by style/room/price/
  colour/material/store/reviews, so more options = a better best-match, not confusion — provided tags are rich
  (they are). Bigger "knows which to recommend" gains come from the deferred OpenAI layer (post-HR phase).

### Sprint 10.25 — HR URL re-verification: dead → needs-review + drifted URLs refreshed
- Acted on the 34 stale URLs the 10.24 image pass found (road-to-production step 3, partial):
  - **16 dead rows → `needs-review`** (URL 301s to a category / a different product — BESTÅ, MÅRUM/STOENSE/
    TIPHEDE/ÅRENDE rugs, RINGSTA/STRANDAD/NYMÅNE/BARLAST lamps, VITTSJÖ, VANDEROTS/GURLI covers, STOFTMOLN,
    ÅRSTID podna→stolna, JYSK VEJLBY/TROSTERUD). The planner's verified-only gate now excludes them.
  - **18 drifted rows → `productUrl` refreshed to the canonical target + web-verified image** (the old URL
    301s to the live product; JYSK BOVRUP/ELVERUM/VEDDE/LIMFJORDEN/AABENRAA/KRISTOF/BELLE/CIRKELHUSE/OLDEKROG/
    RINDSHOLM/MARKSKEL, Emmezeta SLAVE/RETRO/SAWA/MAGNOLIA, IKEA ROEDEBY/SOENDRUM). All 18 got images.
- **Exposed + fixed 2 hidden duplicate products:** canonicalising the drifted URLs made JYSK KRISTOF and
  JYSK BOVRUP collide with an existing canonical row (same product, 2 externalIds, previously different URL
  strings so the 10.23 guard missed them). Deduped: kept living-room KRISTOF (that file is at its JYSK floor;
  merged the dropped row's 3 roomTags + reviews) and new-rooms BOVRUP (its only dining-chair); dropped the
  redundant depth copies. Updated `HrMaxCatalogRuntimeTest` to allow `needs-review` rows.
- Catalog **712 → 710 rows**; HR verified images **236 → 252**; backend **133 tests, 0 failures**.
- **Still open in step 3:** full price + availability re-verification across the (now maxed) HR catalog.

### Sprint 10.24 — verified HR product images (road-to-production step 4)
- **Plumbing first** (committed separately): `imageVerified` end to end — `Product.image_verified`
  (`@ColumnDefault false`), `RetailerProductSnapshotDto`/`ImportProductDto` (+ backwards-compatible ctors),
  `RetailerCatalogAdapter`, `ProductImportService` (set only when an image is present; `inferDataQuality`
  now needs a *verified* image for `complete`), `ProductDto`, frontend `Product` type + `PlanResults`
  (real photo + "ilustracija" chip gated on `imageVerified`).
- **236 / 270 reachable HR images web-verified** (IKEA 112, JYSK 73, Emmezeta 38, Namjestaj.hr 13).
  Technique (deterministic, fabrication-proof): raw-HTTP GET each product page → regex the `og:image` meta
  tag (WebFetch drops meta tags, so no model in the loop) → **product-identity cross-check** (IKEA slug ⊂
  image, Emmezeta id ⊂ image, Namjestaj slug tokens; JYSK host-only as its CDN id is opaque) → confirm the
  image URL resolves (200 + `image/*`; accept Emmezeta `octet-stream` with an image extension). IKEA
  normalised to `?f=xl` (page-sourced asset, verified to resolve). Spot-checked 4 images visually — correct
  products. **No fabrication, no 403-bypass.**
- **Harvey Norman (14) skipped:** its product pages serve a wrong/generic `og:image` (a "patton" page
  returns a "plaza" image) — untrustworthy, placeholder kept.
- **Found 34 stale URLs** (the unimaged ones): ~15 dead (301 → category) + ~18 drifted (301 → live canonical)
  + ÅRSTID podna→stolna. Documented for step 3 in [docs/hr-url-review-10-24.md](docs/hr-url-review-10-24.md);
  this contradicts "HR just verified" → step 3 is not redundant.
- Backend **133 tests, 0 failures**; frontend build clean; dedupe guards still green.

### Sprint 10.23 — catalog hygiene: productUrl dedupe + build-time guards
- **Dedupe (#10b / road-to-production step 3 start).** Collapsed the 6 rows that shared a retailer
  `productUrl` under two `externalId`s to one row each: 2× JYSK KANSTRUP cart + TRAPPEDAL (kept the
  living-room/new-rooms copies the runtime tests reference, removed the `real-hr-kitchen.json` /
  `real-jysk-hr-depth.json` copies), HASLA mattress, HUGO lamp, TAPDRUP. **Unioned `roomTags`** on the kept
  rows so coverage held (TRAPPEDAL kept `kitchen`; HUGO kept `bedroom`+`home-office`) — verified each
  affected runtime test's min-count/category assertions still pass before editing (the living-room file sits
  exactly at its IKEA=41/JYSK=35 floor, so its rows were preserved, not removed).
- **HUGO price conflict resolved honestly.** The two copies disagreed (49.99 € vs 25 €). Web-recheck
  (jysk.hr) showed 25 € is a temporary −50% "Zeleni dani" promo over a **49.99 € regular price**, so the
  merged row keeps the durable 49.99 € (a promo price would go stale the day it ends; the live link always
  shows the current price; step-3 re-verification refreshes anyway). Added verified reviews 4.7/203,
  `lastCheckedAt` 2026-06-17.
- **Build-time guards** in `StoreLinkIntegrityTest`: `noTwoCatalogProductsShareAProductUrl` +
  `noTwoCatalogProductsShareAnExternalId`, both loading `RealCatalogSeeder.snapshotResources()` (the
  authoritative import list) so any future catalog file is covered automatically.
- Catalog **718 → 712 rows** (33 files); backend **132 tests, 0 failures**. No fabrication, no 403-bypass.

### Sprint 10.21 — second-hand marketplace Phase 1 (scaffold, no feed)
- Built the data-model + provenance + guard from the 10.17 design (docs/marketplace-sourcing.md §8),
  behind an unconfigured feed (imports nothing — `Njuškalo`/`Facebook Marketplace` are
  `OFFICIAL_FEED_REQUIRED`, carry no products):
  - **`marketplace-listing`** provenance: in `ProductTaxonomy.SOURCE_TYPES` + `CatalogSourcePolicy`
    (`SOURCE_MARKETPLACE_LISTING`, added to `FEED_SOURCE_TYPES` — it's compliant-feed-delivered).
  - **Njuškalo + Facebook Marketplace** registered (`SUPPORTED_RETAILERS` + policy `OFFICIAL_FEED_REQUIRED`).
  - **`MarketplaceListingFilter`** (the §4 guard): `SOLD_MARKERS` (PRODANO/rezervirano/SOLD/završeno/
    povučeno/nije dostupno… accent-insensitive) + a 24h freshness window + `shouldDrop()` — so a sold or
    expired listing is never ingested.
  - `Product` second-hand columns (data model only): `secondHand` (`@ColumnDefault("false")`),
    `conditionLabel`, `sellerLocation`.
  - Tests: `MarketplaceListingFilterTest` + `MarketplaceSourcingPolicyTest`. Backend **128 tests, 0 failures**.
- **Next = Phase 2** (integrate a compliant Njuškalo/FB feed → implement a `MarketplaceFeed` mapping rows
  to `sourceType=marketplace-listing`, run each through `MarketplaceListingFilter`) and **Phase 3** (a
  separate "Rabljeno" UI section + buyer-beware copy; keep used items out of the new-retail plan total).

### Sprint 10.20 — new EU markets: Italy (IT) + Finland (FI)
- First catalog for **IT (+51)** and **FI (+50, IKEA)** plus **JYSK FI (+15)** = **+116** verified rows.
  IT/FI now cover living-room + bedroom + home-office + kitchen + bathroom + hallway (IKEA); FI also has
  JYSK hallway/kitchen. Files `real-ikea-{it,fi}-rooms.json`, `real-jysk-fi-rooms.json`.
- **IKEA number-trick** ported the verified core+room SKUs to `/it/it/` and `/fi/fi/`; each EUR price was
  re-verified per market (genuinely different — KIVIK 599 IT / 749 FI; STENSTORP cart 169 IT; TÄNNFORSEN
  299 IT / 329 FI). Skipped SKUs that hit category pages / weren't carried (TRONES 2-pack in IT, NYMÅNE
  pendants, FI TARVA bed / STENSTORP); when the number-trick hit a category in FI, the agent found the FI
  canonical URL via search (MALM, LAGKAPTEN). **jysk.fi is NOT JS-gated** (unlike jysk.at) → verified fine.
- Only **EUR** new markets added (IT/FI). Non-EUR EU markets (PL/CZ/HU/RO/SE/DK) deferred: the frontend
  `markets.ts` deliberately offers EUR only ("a non-EUR market needs a currency-correct catalog first").
  IT/FI were already in `Markets.java` + `markets.ts` + city-detection, so no app change was needed.
- `NewMarketsCatalogRuntimeTest` (0 import errors; both markets cover the main rooms); backend **121
  tests, 0 failures**. Catalog snapshot files now **665 rows** (32 files).

### Sprint 10.19 — JYSK SI/DE hallway + kitchen depth
- **JYSK SI (+19), DE (+25)** = +44 verified rows: hallway shoe storage / coat racks / benches / hall
  mirrors / rugs + kitchen carts & wall shelves (those markets previously had JYSK only for
  living-room/bedroom/dining/office). Files `real-jysk-{si,de}-rooms.json`.
- Web-verified on jysk.si / jysk.de single-product pages (name + EUR price + reviews); per-market prices
  differ (BAKHUSE shoe cabinet 65 SI vs 50 DE; ALLESHAVE 42.5 SI vs 40 DE). priceTier recomputed from
  price; colour-suffixed URLs that bounce to a category were skipped.
- **JYSK AT skipped (honest):** jysk.at gates per-product stock behind JavaScript — every single-product
  page renders "Vorübergehend ausverkauft" in the static HTML WebFetch sees (category pages show
  name+price but no stock). Availability can't be confirmed without a feed/API or headless render →
  coverage not forced. Documented for a future feed/headless pass.
- `JyskEuRoomsCatalogRuntimeTest` (0 import errors); backend **120 tests, 0 failures**. Catalog snapshot
  files now **549 rows** (29 files).

### Sprint 10.18 — SI/AT/DE depth: bathroom + hallway + kitchen
- Ported the verified HR IKEA SKUs to **SI (+38), AT (+32), DE (+34)** = **+104** rows, filling the
  bathroom/hallway/kitchen gap those markets had (~0 before). Files `real-ikea-{si,at,de}-rooms.json`.
- **Number-trick** (swap `/hr/hr/` → `/si/sl/` · `/at/de/` · `/de/de/`, keep the trailing product number):
  IKEA redirects to the market product; each row's **EUR price re-verified per market** on ikea.com/<cc>
  and they genuinely differ — never copied across markets. Examples: NYSJÖN mirror cabinet 34.99 SI /
  30 DE / 29.99 AT (39.99 HR); STENSTORP cart 229 SI / 149 DE; TORNVIKEN island 379 SI / 349 DE / 529 AT.
- 3 background subagents (one per market); each ported the 38-SKU list, skipped category-redirect/
  discontinued items (FRIHULT, TJUSIG-wall, NYMÅNE pendants didn't resolve in DE/AT; STENSTORP didn't in
  AT). Spot-checked ~6 across markets on live pages — all matched. priceTier recomputed from price,
  proof fields stripped, "Zadnji kosi" (last-pieces) SI items marked `limited`.
- `EuRoomsDepthCatalogRuntimeTest` (0 import errors, every market covers bathroom/hallway/kitchen);
  backend **119 tests, 0 failures**. Catalog snapshot files now **505 rows** (27 files).

### Sprint 10.17 — HR depth (bathroom/hallway/kitchen) + second-hand marketplace design
- HR **bathroom** depth — the thinnest room (2 → 16). +14 IKEA web-verified: NYSJÖN/ENHET/TÄNNFORSEN
  mirror cabinets + VILTO/STOREDAMM/MUSKAN/IVÖSJÖN/FRÖSJÖN shelf units (storage), KABOMBA/FRIHULT/
  LEDSJÖ/BARLAST lights, LINDBYN/NISSEDAL mirrors (decor; cross-tagged hallway). `real-hr-bathroom.json`.
- HR **hallway** depth (+23): IKEA TRONES/BISSA/STÄLL/MACKAPÄR shoe storage, TJUSIG/NIPÅSEN racks+bench,
  NISSEDAL mirrors, LOHALS/MORUM rugs, NYMÅNE light; JYSK BELLE/VANDSTED/CIRKELHUSE/EGTVED/OLDEKROG +
  SANDFIOL rug; Emmezeta Sawa/Anter/Valencia. `real-hr-hallway.json`.
- HR **kitchen** depth (+14): IKEA NYMÅNE pendants, HULTARP/KUNGSFORS rails+grids, STENSTORP/TORNVIKEN/
  BROR/LOSHULT carts; Emmezeta Magnolia/Modena/Grey/Clara cabinets. `real-hr-kitchen-depth.json`.
- Each row web-verified on its **live public product page** on 2026-06-16 (`sourceType=public-product-page`,
  no fabrication); clearance ("Zadnja prilika za kupnju") items dropped so links don't die. Discovery via
  `WebSearch` (allowed_domains) → `WebFetch` category page → `WebFetch` each `/p/` page (fanned out 2
  subagents for hallway/kitchen, spot-checked the results).
- `HrDepthCatalogRuntimeTest` (0 import errors over the 3 files); backend **118 tests, 0 failures**.
- **Second-hand marketplace section — designed** (design-first, no code yet):
  [docs/marketplace-sourcing.md](docs/marketplace-sourcing.md). Feed/API model (Njuškalo/FB are
  `OFFICIAL_FEED_REQUIRED`, never scraped), new `marketplace-listing` provenance + `second-hand` flag,
  an aggressive **sold/expired guard** (drop `PRODANO`/reserved/dead listings on ingest + 24h freshness),
  a separate "Rabljeno" UI section, no affiliate/sponsored on used items.

### Sprint 10.16 — HR kitchen + retailer expansion
- HR **kitchen** depth (+15: IKEA/JYSK/Emmezeta — carts, wall storage, pendants).
- **New verified retailers** (web-verified products): Harvey Norman (HR 9 + SI 6), Namjestaj.hr (HR 9),
  Otto (DE 6), Segmüller (DE 6), Poco (DE 2). Catalog now ~493 products.
- Registered **all targeted retailers** in `ProductTaxonomy.SUPPORTED_RETAILERS` + `CatalogSourcePolicy`.
  Probed fetchability of every named chain — most big ones are bot-blocked (see classification below)
  → `OFFICIAL_FEED_REQUIRED`, no products until a feed.
- Added `NewRetailersCatalogRuntimeTest`; backend 117 tests, 0 failures.

#### Retailer fetchability assessment (2026-06-16)
| Country | Verified (have products) | Blocked / unusable → feed-required |
|---|---|---|
| HR | Harvey Norman, Namjestaj.hr (+ IKEA/JYSK/Emmezeta) | Momax, Prima Namještaj, Bauhaus, FeroTerm (403/refused), Perfecta Dreams (JS-only prices) |
| SI | Harvey Norman | Momax, Lesnina/XXXLutz (403), Dipo, Merkur (garden-only, out of scope) |
| DE | Otto, Segmüller, Poco | Wayfair (closed in DE), Home24 (403), Roller (JS-only) |
| AT | — | Kika, Leiner, Momax, XXXLutz (403/TLS/refused) |

**Re-probe 2026-06-17 (Sprint 10.24) — more HR shops, looking for new importable retailers:** none usable.
- **Reachable but JS-only prices → feed-required:** `mojnamjestaj.hr` ("Moj namještaj"; static name + `og:image`,
  but WooCommerce price element is empty in static HTML — JS-rendered), `vitapur.hr` (bedding/home; shows leftover
  `Kn`/`0,00 €` placeholders), `prima-namjestaj.hr` (homepage 200, prices still JS — confirms 10.16).
- **403 / Cloudflare "Just a moment…":** `moemax.hr` (Mömax), `sancta-domenica.hr`, `mraz.hr`, `lesnina.hr`.
- **Not furniture / dead domains:** `top-shop.hr` (now real-estate), `mobelix.com` (for sale), several mis-guessed `.hr`.
- **Conclusion:** the directly-importable HR universe stays IKEA / JYSK / Emmezeta / Harvey Norman / Namjestaj.hr.
  Everything else is JS-priced or WAF-blocked → an official/partner feed (we never bypass 403 or fabricate a JS price).
  Default `CatalogSourcePolicy.statusFor` already treats these unvetted names as `OFFICIAL_FEED_REQUIRED`.

### Sprint 10.15 — production catalog depth
- Web-verified **~150 new products** across retailers × markets (no fabrication; each verified on the
  live public product page, `sourceType=public-product-page`):
  - IKEA depth: SI (+17), AT (+23), DE (+24) — beds, mattresses, nightstands, wardrobes, dining,
    extra sofas/storage/lighting/decor.
  - JYSK: HR (+25 depth), SI (+15 new), AT (+14 new), DE (+17 new).
  - Emmezeta HR (+17 depth).
- Catalog now ~440 products; SI/AT/DE cover living-room + bedroom + dining + home-office.
- Added `"dining"` room alias; `ProductionDepthCatalogRuntimeTest` (0 import errors over all depth files).
- Built `ARCHITECTURE.md` / `MEMORY.md` / `TASKS.md` for session continuity.

### Sprint 10.14 — sourcing policy + feeds + EU markets
- `CatalogSourcePolicy` (403→feed, never bypass) + `isProductionVerified` gate; collector refuses
  feed-required retailers; import-source provenance vocabulary; `docs/sourcing-policy.md`.
- `ai.budgetspace.feed` scaffolding (RetailerFeed + unconfigured default + importer that cleanly skips).
- Verified IKEA Austria (AT) and Germany (DE) catalogs (first after Slovenia).
- UX: market badge, honest "ilustracija" marker for missing images.
- Fixes: `is_sponsored` startup crash, DevTools restart loop, CORS `X-BudgetSpace-Session`, client.ts
  Content-Type, dev ports → frontend 5180 / backend 8090.

## Backlog (next steps, roughly prioritised)

1. **Turn on the LLM (OpenAI) carefully.** Set `BUDGETSPACE_AI_ENABLED=true`,
   `BUDGETSPACE_LLM_PROVIDER=openai`, `OPENAI_API_KEY=...` (backend env only). Verify `AiUsageTracker`
   caps (monthly USD / per-day / per-session). The rule-based path stays the fallback. Catalog depth
   is now sufficient to test prompts without burning keys on "no products" runs.
2. **More catalog depth where thin.** HR bathroom/hallway/kitchen (10.17); SI/AT/DE bathroom/hallway/
   kitchen IKEA (10.18); JYSK SI/DE hallway/kitchen (10.19). **Next:** new EU markets IKEA/JYSK (IT, FI —
   EUR; in progress 10.20); JYSK AT hallway/kitchen once jysk.at stock is feed/headless-readable;
   Emmezeta-style HR retailers for more non-IKEA breadth. Non-EUR EU markets (PL/CZ/HU/RO/SE/DK) need
   currency-correct UI first (frontend offers EUR only) — defer. Same rule: verify each live.
3. **First real `RetailerFeed`.** When a Decathlon/Pevex/Lesnina official or affiliate feed is
   available, implement `RetailerFeed` (replaces the `ConfigBackedRetailerFeed` bean) → unlocks
   `home-gym` and removes the last sample-data dependency.
4. **Product image verification status.** Add an image-verification field; only show real images when
   verified, else keep the labelled placeholder.
5. **Product-click / affiliate analytics** (backend-friendly; tracking endpoints already exist) — next
   monetization step without harming UX.
6. **Flip planner to verified-only** (`CatalogSourcePolicy.isProductionVerified`) once every room is
   sourced — then retire `data.sql` sample fallback. Recalibrate planner tests.
7. **Refresh `dataQuality`** from `partial` → re-verify prices/stock before a real production launch.
8. Add more EU markets only when their catalog is sourced. **IT + FI done (10.20, EUR).** Non-EUR
   (PL/CZ/HU/RO/SE/DK): need currency-correct UI first (frontend `markets.ts` is EUR-only) — do that UI
   work before sourcing their catalogs. Optionally flip `available:true` in `markets.ts` for SI/AT/DE/IT/FI
   to expose them in the picker (currently only HR is "available"; the rest are catalog-ready "coming soon").
9. **Second-hand marketplace section (Njuškalo, FB Marketplace).** ✅ **Designed (10.17)** +
   ✅ **Phase 1 built (10.21)** — [docs/marketplace-sourcing.md](docs/marketplace-sourcing.md):
   `marketplace-listing` provenance, Njuškalo/FB registered as `OFFICIAL_FEED_REQUIRED`,
   `MarketplaceListingFilter` (sold/expired guard, tested), `Product` second-hand columns. No feed/data/UI
   yet. **Next = Phase 2** (a `MarketplaceFeed` over a compliant Njuškalo/FB API/export → rows with
   `sourceType=marketplace-listing`, each run through `MarketplaceListingFilter`; never scrape) and
   **Phase 3** (separate "Rabljeno" UI section + buyer-beware copy; used items stay out of the new-retail total).

10b. **[x] Catalog hygiene: dedupe duplicate productUrls.** ✅ **Done 2026-06-17 (Sprint 10.23).** The 6
   pre-existing rows that shared a retailer URL under two `externalId`s (2× KANSTRUP cart, TRAPPEDAL, HASLA
   mattress, HUGO lamp, TAPDRUP) collapsed to one row each (kept the `externalId` referenced by tests,
   unioned `roomTags` so no room/category coverage was lost). **HUGO had a genuine price conflict** (49.99 €
   vs 25 €): web-recheck showed 25 € is a temporary −50% "Zeleni dani" promo over a 49.99 € regular price, so
   the merged row keeps the durable **regular 49.99 €** (+ verified reviews 4.7/203, `lastCheckedAt`
   2026-06-17). Added two build-time guards in `StoreLinkIntegrityTest` (no duplicate `productUrl`, no
   duplicate `externalId`) that load the seeder's authoritative resource list so future files are covered.
   Catalog 718 → **712 rows**; backend **132 tests, 0 failures**.
10. **Bring blocked retailers online via feeds.** The big chains we probed (Otto beyond rate-limits,
   Wayfair, Home24, Roller, XXXLutz/Kika/Leiner, Momax, Bauhaus, FeroTerm, Lesnina, Decathlon, Pevex,
   Merkur, Dipo) are registered as feed-required — integrate an official/affiliate feed per the
   `ai.budgetspace.feed` seam when available. Never scrape them.

## Manual test prompts (rule-based, no LLM spend)
Pick the country (top-right) to match the market, then paste the wish. Markets with data: HR, SI, AT, DE.

- **HR · dnevni boravak**: „Imam 1500 € za dnevni boravak, moderno, najviše IKEA, već imam TV i tepih."
- **HR · spavaća**: „Spavaća soba do 1200 €, minimalistički, trebam krevet, madrac, ormar i noćne ormariće."
- **HR · blagovaonica**: „Blagovaonica do 800 €, kombiniraj IKEA i JYSK, trebam stol i 4 stolice."
- **HR · kuhinja**: „Kuhinja do 600 €, trebam kuhinjska kolica, zidnu policu i rasvjetu." (kitchen depth)
- **DE · Wohnzimmer (all IKEA)**: „Imam 1800 € za dnevni boravak i želim sve iz IKEA-e, svijetli stil, već imam TV."
- **DE · spavaća (complete)**: „Spavaća soba 1500 €, kompletno, minimalistički — krevet, madrac, ormar, komoda."
- **AT · radni kutak**: „Radni kutak do 600 €, moderno, trebam radni stol, uredsku stolicu i policu."
- **SI · blagovaonica**: „Jedilnica do 1000 €, moderno, miza in stoli." (planner razumije i HR/EN pojmove)
- **HR · home-gym (još na sample podacima)**: očekuj djelomičan plan / placeholder dok ne dođe Decathlon feed.

Expected: 3 plans (value/budget/stretch), real product names + EUR prices + "Otvori u trgovini" links,
market badge for non-HR, no fake ratings, TV/tepih excluded when "već imam …".
