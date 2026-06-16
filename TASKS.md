# BudgetSpace AI — Tasks

Living backlog + done log. Pair with `MEMORY.md` and `ARCHITECTURE.md`.

## Recently done

### Sprint 10.18 — SI/AT/DE depth: bathroom + hallway + kitchen (current)
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
2. **More catalog depth where thin.** HR bathroom/hallway/kitchen done in 10.17; SI/AT/DE
   bathroom/hallway/kitchen (IKEA) done in 10.18. **Next:** JYSK SI/AT/DE kitchen/hallway (still
   living-room/bedroom/dining/office only); Emmezeta-style HR retailers for more non-IKEA breadth; raise
   per-market counts toward HR parity. Same rule: verify each on the live product page.
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
8. Add more EU markets (IT/FI/PL/…) only when their catalog is sourced (else they return empty plans).
9. **Second-hand marketplace section (Njuškalo, FB Marketplace).** ✅ **Designed in 10.17** —
   [docs/marketplace-sourcing.md](docs/marketplace-sourcing.md) (feed/API not scraping; `marketplace-listing`
   provenance + `second-hand` flag; sold/expired guard; separate "Rabljeno" UI). **Next = Phase 1 build:**
   data-model columns + `marketplace-listing` in `SOURCE_TYPES` + `MarketplaceListingFilter` (SOLD_MARKERS
   + 24h freshness) + tests, behind an unconfigured feed (imports nothing) — same scaffold-first approach
   as 10.14. Phase 2 = integrate a compliant Njuškalo feed if/when one exists.

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
