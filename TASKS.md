# BudgetSpace AI — Tasks

Living backlog + done log. Pair with `MEMORY.md` and `ARCHITECTURE.md`.

## Road to production (sequenced — added 2026-06-16)

Goal path: **controlled HR beta first**, then full multi-market launch. Catalog + tests + architecture
are solid; the work to production is data freshness, AI enablement, market/UX exposure and prod hardening
— not core code. Do the 5 steps in order; check off as done.

### Next 5 steps (toward a HR beta)

1. **[ ] Planner verified-only gate (catalog integrity end-to-end).** Make `PlannerService` build plans
   only from `CatalogSourcePolicy.isProductionVerified` products. For rooms with a verified catalog, never
   serve `data.sql` sample rows; for rooms without one (e.g. `home-gym`), show an honest empty-state
   ("još nemamo provjerene proizvode za ovu sobu") instead of placeholders. Recalibrate planner tests.
   *Done when:* no sample/needs-review/stale row can appear in any plan; home-gym degrades gracefully; tests green.
2. **[ ] Enable OpenAI LLM with enforced cost caps** (needs `OPENAI_API_KEY` from the owner — backend env
   only, never committed). Set `BUDGETSPACE_AI_ENABLED=true`, `BUDGETSPACE_LLM_PROVIDER=openai`. Verify
   `AiUsageTracker` monthly-USD + per-day + per-session caps actually block past the limit, and that the
   rule-based path fires on every failure (timeout, bad JSON, network, cap hit). Measure prompt→`PlannerInputDto`
   quality + token cost on the rich catalog using the manual test prompts. *Done when:* AI improves prompt
   understanding, never exceeds caps, always falls back cleanly; no key in repo/logs/`.env.example`.
3. **[ ] HR data freshness / re-verification before launch.** Re-verify prices + availability for the HR
   catalog (the beta market); flip confirmed rows `partial → complete`, mark drifted/vanished ones
   `needs-review`. Use/extend `CatalogHealthService` for a stale-rows report + a re-check cadence. Dedupe the
   6 duplicate `productUrl`s (#10b below). *Done when:* HR catalog is fresh + `complete` where verified, with a documented re-check cadence.
4. **[ ] HR launch UX + observability.** Run the app and do a UX pass: plan quality, market badge, honest
   "ilustracija" image labels + disclaimers, store-link correctness. Wire basic analytics (the product-click /
   plan-feedback endpoints already exist) + error monitoring + structured plan-generation logging. Decide the
   image strategy (verified images for top items OR keep the labelled placeholder — never fabricate URLs).
   *Done when:* the HR flow is polished, instrumented and observable.
5. **[ ] Security review + deploy infra + go-live checklist (HR beta).** Run a security review (keys backend-only,
   CORS, admin-endpoint guard active in prod, no secrets in logs/`.env.example`, input validation). Stand up
   production deployment: hosting, env-based config for keys/feeds, **switch DB off `ddl-auto=create`** (it
   wipes the schema every start — add Flyway/Liquibase migrations + backups), CI/CD, HTTPS. Legal/GDPR for a
   HR consumer app + affiliate/sponsored disclosure copy. *Done when:* a controlled HR beta can go live safely.

> ⚠️ **Hard prod blocker to remember:** `spring.jpa.hibernate.ddl-auto=create` rebuilds (wipes) the DB on
> every startup — fine for dev, fatal for prod. Must move to validate + versioned migrations before any
> real deployment (folded into step 5).

### Further steps (post-HR-beta → full multi-market production)

- **Expose + localize EU markets**: flip `available:true` in `frontend/src/markets.ts` for SI/AT/DE/IT/FI and
  translate the non-HR UI (currently `lang:'en'`); confirm per-market currency formatting end-to-end.
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

### Sprint 10.21 — second-hand marketplace Phase 1 (scaffold, no feed) (current)
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

10b. **Catalog hygiene: dedupe duplicate productUrls.** 6 pre-existing rows (from sprints ≤10.16) point
   the same retailer URL under two `externalId`s (e.g. JYSK KANSTRUP carts in both `real-hr-kitchen.json`
   and `real-jysk-hr-new-rooms.json`; TRAPPEDAL; a JYSK madrac/lamp/noćni ormarić). Both import as
   separate rows → redundant catalog entries. Pick one `externalId` per URL and drop the other; add a
   build-time guard test against duplicate productUrls. Not introduced by 10.17 (its 51 rows add 0 dup URLs).
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
