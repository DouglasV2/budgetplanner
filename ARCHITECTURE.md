# BudgetSpace AI — Architecture

Prompt-first furniture & room budget planner. The user writes a free-text wish ("Imam 1500 € za
dnevni boravak, moderno, već imam TV") and gets 3 concrete, priced shopping plans built from a
**local, web-verified product catalog**.

## Stack & dev ports
- **Frontend**: React + Vite + TypeScript (`frontend/`). Dev served on **http://localhost:5180**.
- **Backend**: Spring Boot 3.3.5, Java 17/21 (`backend/`). REST API on **http://localhost:8090**.
- **DB**: PostgreSQL 16 (docker) on 5432. `ddl-auto=create` → schema rebuilt each start; `data.sql`
  seeds samples, then `RealCatalogSeeder` imports verified catalog snapshots.
- **Run dev**: `docker compose up` (compose + override). Backend boots ~30-40s; wait for
  `Started BudgetspaceApplication` before using the app. DevTools auto-restart is **disabled** in the
  override (it loop-restarted under the Windows bind mount); restart the backend container manually to
  pick up backend code changes.

## Request flow (plan generation)
1. Browser `POST /api/plans/generate` (sends `Content-Type: application/json` + `X-BudgetSpace-Session`).
2. `PlanController` → `PlannerService.generate()`.
3. Prompt understanding: **AI is OFF by default** → deterministic `PlannerIntentExtractor` parses the
   prompt into `PlannerInputDto`. When AI is enabled, `PromptIntelligenceService` (provider-agnostic
   `LlmClient`, OpenAI-first) does it, with rule-based fallback on every failure. The LLM never invents
   products — it only structures the prompt.
4. `PlannerService` builds 3 plans (value / budget / stretch) by picking **real catalog products**
   from `marketCatalog(input)` (products filtered to the request's `market`), scored by style, room,
   price, colour/material, store-count, etc. Never fabricates.

## Backend packages (`backend/src/main/java/ai/budgetspace/`)
- **planner/** — `PlannerService` (core selection + budget repair + plan copy), `PlannerIntentExtractor`
  (rule-based prompt parsing), `PromptIntelligenceService` (LLM layer), `DesignAssistantService`.
- **product/** — `Product` (JPA entity), `ProductImportService` (validation + taxonomy + dedup by
  externalId), `RetailerSnapshotImportService` + `RetailerCatalogAdapter` (snapshot → import),
  `RealCatalogSeeder` (loads `/catalog/*.json` on startup; **register new catalog files here**),
  `ProductTaxonomy` (canonical categories/styles/rooms + aliases + freshness), `Markets` (HR/SI/AT/DE/…
  currency+locale), `CatalogSourcePolicy` (sourcing rule — see below), `CatalogHealthService`.
- **collector/** — `RetailerCollectorService` (controlled URL-pack fetch for IKEA/JYSK; **refuses
  feed-required retailers**), `HttpProductPageFetcher` (plain JDK HTTP, honest UA, 403 = clean fail).
- **feed/** — official/partner feed seam: `RetailerFeed` interface, `ConfigBackedRetailerFeed`
  (unconfigured by default), `RetailerFeedProperties` (env-backed, blank), `RetailerFeedImporter`
  (startup; cleanly skips unconfigured feeds, never crashes). No credentials committed.
- **ai/** — `LlmClient` (+ `OpenAiLlmClient`/`AnthropicLlmClient`), `LlmClientFactory`, `LlmProperties`,
  `AiUsageTracker` (monthly-USD + per-day + per-session caps). Off by default.
- **saved/**, **tracking/** — saved plans; product-click / plan-feedback events.
- **config/** — `CorsConfig` (allows the frontend origin + `X-BudgetSpace-Session` header),
  `AdminEndpointGuardFilter` (hides admin/collector/import endpoints in prod), `GlobalExceptionHandler`.

## Catalog & sourcing (the core domain rule)
- Verified catalog snapshots live in `backend/src/main/resources/catalog/*.json` (shape =
  `RetailerProductSnapshotDto`). `data.sql` still seeds legacy sample rows for not-yet-sourced rooms.
- **`CatalogSourcePolicy` is the single source of truth.** A 403 is never bypassed (no proxy / stealth
  / fingerprint / cookies / private endpoints). Per-retailer status:
  - `DIRECT_VERIFIED` (pages fetchable + hand-verified, in collector allowlist): IKEA, JYSK
  - `MANUAL_VERIFIED_ONLY` (verified, link-out, have products): Emmezeta, Harvey Norman, Namjestaj.hr,
    Otto, Segmüller, Poco
  - `OFFICIAL_FEED_REQUIRED` (403/anti-bot/JS-only/out-of-scope → only an official/partner feed, no
    products yet): Decathlon, Pevex, Lesnina, Momax, Prima Namještaj, Perfecta Dreams, Bauhaus,
    FeroTerm, Merkur, Dipo, Wayfair, Home24, Roller, Kika, Leiner, XXXLutz
  - Adding a retailer = add to `ProductTaxonomy.SUPPORTED_RETAILERS` + `CatalogSourcePolicy` status
    (+ `PlannerService.RETAILERS` if it has products). Most big chains are bot-blocked (confirmed by
    probing) → they wait for a feed.
- Import-source provenance (`Product.sourceType`): `manual-verified`, `public-product-page`,
  `official-feed`, `affiliate-feed` (+ legacy `manual`/`retailer-snapshot`/`future-scraper`).
- `CatalogSourcePolicy.isProductionVerified(Product)` = the verified gate (canEnterPlanner AND not
  stale AND has sourceReference AND, if feed-required, came from a feed) → excludes NEEDS_REVIEW /
  STALE / sample. Full rules: [docs/sourcing-policy.md](docs/sourcing-policy.md).
- **Markets with data**: HR (deep — every room incl. bathroom/hallway/kitchen), SI/AT/DE (IKEA + JYSK
  depth: living-room/bedroom/dining/home-office; kitchen/hallway/bathroom still TODO). IT/FI/PL/CZ/HU/RO/
  SE/DK exist in `Markets` but have **no catalog** → empty plan. JYSK/IKEA verified per market (prices
  differ per country — never copied across markets). Emmezeta = HR only.
- **Second-hand marketplaces** (Njuškalo, FB Marketplace) are a designed-but-unbuilt future source —
  feed/API only (never scraped), with a sold/expired guard and a separate "Rabljeno" section. Design:
  [docs/marketplace-sourcing.md](docs/marketplace-sourcing.md).

## Invariants (do not break)
- **Never fabricate** product name/price/URL/image/review. Unverifiable → `needs-review`, image null.
- AI **off by default**; API keys **backend-only** (never `VITE_*`, README, logs, fixtures, or real
  values in `.env.example`). `.env` git-ignored.
- `reviewRating`/`reviewCount` are **display-only**, separate from the planner's internal `rating`.
- Value-first monetization: core plan free; `affiliateUrl` never replaces `originalProductUrl`;
  sponsored is labelled and never displaces the best organic pick.

## Build & test
- Backend (this Windows box): no `mvn` on PATH — use `C:\Program Files\Java\jdk-21` +
  re-downloaded `apache-maven-3.9.9` + credential-free `C:\Users\bpusic\.m2\settings-central.xml`
  (Central only). `mvn -s <settings> -f backend/pom.xml test` (no DB needed — plain JUnit/Mockito).
- Frontend: `cd frontend && npm run build` (`tsc -b && vite build`).
- See [backend/README.md](backend/README.md) and `TASKS.md`.
