# BudgetSpace AI — Project memory (for future sessions)

Read this + `ARCHITECTURE.md` + `TASKS.md` first when picking the project back up. This captures the
non-obvious decisions and current state that aren't derivable from the code alone.

## What this is
A prompt-first furniture/room budget planner (see `ARCHITECTURE.md`). Goal: the user types a wish,
gets 3 concrete priced shopping plans from a **real, web-verified** catalog. Croatian-language UI.

## Direction & committed decisions
- **Honesty over coverage.** Never fabricate a product/price/URL/image/review. Every catalog row is
  web-verified on the live product page. Unverifiable → leave out or `needs-review`; image → null.
- **403 is an architecture decision, not a scraping problem.** Retailers that block bots (Decathlon,
  Pevex, Lesnina — HTTP 403) are `OFFICIAL_FEED_REQUIRED`; we never bypass (no proxy/stealth/
  fingerprint/cookies/private endpoints). They wait for an official/partner feed. Codified in
  `CatalogSourcePolicy` + `docs/sourcing-policy.md`.
- **AI is the parser, not the picker.** LLM (provider-agnostic, **OpenAI-first**) only turns the
  free-text prompt into structured input; the deterministic planner still picks real products. AI is
  **OFF by default** with rule-based fallback on every failure path. Keys are backend-only.
- **Why build catalog depth before turning on the LLM (current focus):** the user wants enough
  verified products that the rule-based planner is production-ready, so OpenAI keys aren't burned on
  testing once enabled. That's why sprint 10.15 fanned out to ~150 verified products.
- **Value-first monetization.** Core plan free; affiliate/sponsored fields exist but `affiliateUrl`
  never replaces `originalProductUrl`; sponsored is discreet + labelled. No Stripe.

## Current state (as of 2026-06-16)
- Backend tests: **130 green, 0 failures** (baseline grows each sprint; was 92 mid-10.x, 117 in 10.16).
- Catalog snapshot files: **718 web-verified rows** across 33 files (seeded total ~770 incl. data.sql
  samples). IKEA is the bulk. Recent: 10.17 +51 (HR bathroom/hallway/kitchen); 10.18 +104 (SI/AT/DE
  bathroom/hallway/kitchen IKEA); 10.19 +44 (JYSK SI/DE hallway/kitchen); 10.20 +116 (new markets IT 51 +
  FI 50 IKEA + JYSK FI 15); 10.22 +53 (HR gap-fill + non-IKEA breadth → HR is now ~290 sourced rows, every
  planner-flow room×category cell covered).
- **Markets with real catalog: HR (deep — all rooms), SI, AT, DE, IT, FI.** SI/AT/DE/IT/FI cover
  living-room + bedroom + home-office + kitchen + bathroom + hallway (IKEA; SI/AT/DE also dining). JYSK
  covers hallway/kitchen for **SI + DE + FI** (not AT — jysk.at gates stock behind JS, "Vorübergehend
  ausverkauft" in static HTML → can't confirm availability; needs feed/API). Non-EUR EU markets
  (PL/CZ/HU/RO/SE/DK) deferred (frontend is EUR-only). Per-market prices verified individually (they genuinely
  differ: KALLAX 169 DE / 189 AT / 199 SI / 179 HR). Other countries in `Markets` (IT/FI/PL/CZ/HU/RO/
  SE/DK) have **no catalog** → empty plan (expected).
- **Retailers** (single source of truth = `CatalogSourcePolicy`):
  - Verified/with-products: IKEA, JYSK (HR/SI/AT/DE), Emmezeta (HR), **Harvey Norman (HR/SI),
    Namjestaj.hr (HR), Otto/Segmüller/Poco (DE)**.
  - Registered but **feed-required** (403/anti-bot/JS-only/out-of-scope → no products yet): Decathlon,
    Pevex, Lesnina, Momax, Prima Namještaj, Perfecta Dreams, Bauhaus, FeroTerm, Merkur, Dipo, Wayfair,
    Home24, Roller, Kika, Leiner, XXXLutz. (Most big chains are bot-blocked — confirmed by probing.)
  - `home-gym` still relies on sample data (needs a Decathlon feed).
- **Second-hand marketplace (Njuškalo, FB Marketplace): designed 10.17 + Phase 1 built 10.21** →
  `docs/marketplace-sourcing.md`. Feed/API model (never scrape; both `OFFICIAL_FEED_REQUIRED`). Phase 1
  (scaffold, no feed/data/UI): `marketplace-listing` provenance (in `SOURCE_TYPES` + `FEED_SOURCE_TYPES`),
  Njuškalo/FB registered, `MarketplaceListingFilter` (SOLD_MARKERS PRODANO/rezervirano/… + 24h freshness +
  `shouldDrop`), `Product.secondHand`/`conditionLabel`/`sellerLocation` columns, tests. Next: Phase 2 =
  a `MarketplaceFeed` over a compliant API/export; Phase 3 = separate "Rabljeno" UI (out of new-retail total).
- App runs via `docker compose up`: frontend **:5180**, backend **:8090**, postgres :5432. After
  `docker compose up`, wait for the seed to finish (log "Real catalog seed: done") before the first
  plan request — a request mid-seed can return 0 items (race, not a bug).

## Hard-won gotchas (cost real debugging time)
- `data.sql` sample rows omit `is_sponsored` (a NOT NULL col since 10.10) → on PostgreSQL the seed
  insert crashed the app on startup. Fixed with `@ColumnDefault("false")` on `Product.sponsored`.
- Spring Boot **DevTools restart-loops** under the Docker bind mount on Windows (watches `target/`,
  which `mvn spring-boot:run` rewrites) → intermittent `ERR_EMPTY_RESPONSE` / CORS. Disabled via
  `SPRING_DEVTOOLS_RESTART_ENABLED=false` in `docker-compose.override.yml`.
- **CORS**: `/api/plans/generate` sends a custom `X-BudgetSpace-Session` header → it MUST be in
  `CorsConfig` allowed headers or the browser preflight is rejected ("no Access-Control-Allow-Origin").
- **client.ts** must spread `...options` BEFORE `headers:` or a call passing its own headers drops
  `Content-Type`, the browser sends `text/plain`, and the backend 500s.
- Ports 5173/8080 were occupied by stray processes on the dev machine → moved frontend→5180,
  backend→8090 (with CORS + `VITE_API_BASE_URL` updated to match).
- Build needs the jdk-21 + downloaded-Maven + `settings-central.xml` workaround (see `ARCHITECTURE.md`).

## Known limitations
- ~~Planner not restricted to verified products~~ **RESOLVED (2026-06-16, road-to-prod step 1):**
  `PlannerService.marketCatalog` is gated on `CatalogSourcePolicy.isPlannerEligible` (= `isProductionVerified`
  minus staleness), so `data.sql` sample rows / `needs-review` / blocked-without-feed never reach a plan.
  Stale rows still enter (with a warning) so the catalog doesn't empty as it ages. Rooms with no sourced
  catalog (e.g. `home-gym`) now return an honest empty/partial plan instead of sample placeholders.
- Catalog images are mostly null (placeholder shown in UI, labelled "ilustracija") — we don't
  fabricate image URLs.
- `dataQuality` of all imported rows is `partial` ("re-check before production") — prices/stock should
  be refreshed before a real launch.

See `git log` for sprint-by-sprint history; the most recent sprints (10.14 sourcing policy + feeds +
EU markets, 10.15 catalog depth) are summarised in `TASKS.md`.
