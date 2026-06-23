# BudgetSpace AI — Tasks

Living backlog + done log. Pair with `MEMORY.md` and `ARCHITECTURE.md`.

## ⚠️ Placeholders & seams — replace with the real thing (don't ship as-is)

A running registry of everything intentionally left as a placeholder / dormant seam, so we never forget to wire
the real thing. Each line says what activates it. (Added 2026-06-19.)

- **eBay Browse "Rabljeno" feed** (10.51 → ✅ reworked **10.64**) — now a **live, request-time, in-memory-only**
  source: `EbayBrowseFeed` is a `@Service` (no longer a `RetailerFeed`/import bean) that the planner calls when
  building a plan; results map to **transient `Product`s, returned in that one response, NEVER written to the DB**
  (honours the owner's eBay "do not persist" declaration + eBay ToS). A short in-memory cache (10 min) spares the
  rate limit. PRODUCTION keys are wired via the gitignored `.env`
  (`BUDGETSPACE_MARKETPLACEFEEDS_EBAY_CLIENTID/_CLIENTSECRET`; the Cert ID is a real secret, never committed).
  Markets: DE/IT/AT/FR/NL/ES/GB (eBay has no local site elsewhere). Blank keys → dormant. **Rotate the Cert ID**
  (it was shared in chat). Possible tuning: the furniture category id (3197) if a market returns thin results.
- **Per-country marketplace feeds** (10.49) — Njuškalo (HR), Bolha (SI), Willhaben (AT), Kleinanzeigen (DE), Subito
  (IT), Tori (FI), Leboncoin (FR), Marktplaats (NL), Bazoš (SK), Wallapop (ES), OLX (PT), Finn (NO), Blocket (SE),
  DBA (DK), Facebook Marketplace — registered `OFFICIAL_FEED_REQUIRED`, import 0. Activate: a compliant
  partner/affiliate/export feed each (build a `MarketplaceFeed` like `EbayBrowseFeed`). **Never scrape.**
- **Google sign-in** (10.53 → ✅ **10.63**) — real Google Sign-In now ships: a sign-in front door (with a guest
  escape), local Google ID-token verification (Nimbus, no client secret), HttpOnly cookie sessions with absolute
  + idle expiry (auto-logout), and guest→account saved-plan migration on first sign-in. Live only once the OAuth
  client has `http://localhost:5180` (and the prod origin) in its Authorized JavaScript origins, and
  `BUDGETSPACE_GOOGLE_CLIENTID` is set (public client id, supplied via the gitignored `.env`). Blank → dormant.
- **Moodboard upload** (10.58) — disabled "Učitaj moodboard · uskoro" placeholder in the vibe picker. Activate: a
  vision/AI layer (uploaded room image → inferred style + palette). Gated by the AI layer being enabled.
- **AI layer (Gemini default)** (10.66) — `BUDGETSPACE_AI_ENABLED=false` by default (rule-based planner runs).
  Provider-agnostic; **Gemini Flash added as the cheapest default** (free tier) — the "Plus" carrot (smart prompt
  understanding + design rationale). Activate: set `BUDGETSPACE_AI_ENABLED=true` + `BUDGETSPACE_LLM_PROVIDER=gemini`
  + `GEMINI_API_KEY` (gitignored `.env`), then verify `AiUsageTracker` caps + rule fallback. The earlier "defer
  until EU catalog" hold was lifted (2026-06-19) because Gemini's free tier removes the cost-of-testing concern.
- **Price-drop email alerts** (10.34) — `PriceWatchNotifier` is a log-only seam + `recheck-enabled=false`. Activate:
  wire a real email provider + flip `budgetspace.price-watch.recheck-enabled=true`.
- **Retailer feeds for blocked shops** (10.14) — Decathlon/Pevex/Lesnina + other `OFFICIAL_FEED_REQUIRED` chains
  carry no products (403/anti-bot, never scraped). Activate: an official/partner/affiliate `RetailerFeed` (seam in
  `ai.budgetspace.feed`). `home-gym` depends on Decathlon → still partly sample data.
- **Affiliate / sponsored monetisation** (10.10) — `affiliateUrl`/`sponsored`/`sponsorLabel` columns exist; no
  affiliate network wired, no sponsored UI. Activate: an affiliate feed + a clearly-labelled sponsored slot that
  never displaces the best organic pick.
- **eBay seller images** (10.51) — used listings ship `imageVerified=false` → the UI shows the labelled placeholder,
  not the seller photo, until eBay's image display-rights terms are confirmed. Activate: confirm rights, then flip.
- **Prod DB** — `spring.jpa.hibernate.ddl-auto=create` wipes the DB on every startup (dev only). Before Railway:
  switch to `validate` + Flyway/Liquibase versioned migrations (the new session_id / second-hand / GB columns need it).

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

- **★ Discount / sale tracking + price-drop alerts (owner-requested 2026-06-17, do AFTER EU depth + per-language).**
  A retention/hook feature: surface real sales and (opt-in) notify a user when a product they care about goes
  on sale. **Strictly web-verified — never fabricate a discount, a regular price, or a sale window** (same rule
  as everything else; a fake "−40%" is worse than none).
  - **Data model.** ✅ **Done (Sprint 10.33).** Added `Product.saleEndsAt` end to end (entity + snapshot/import
    DTOs + adapter — which previously hard-coded `originalPrice=null`! — + `ProductDto` → frontend `Product`
    type); import now validates `originalPrice>0` and a parseable `saleEndsAt`. `price` = current, `originalPrice`
    = regular, a product is "on sale" when `price < originalPrice`. **Populated 24 real verified JYSK HR sales**
    (deterministic live read: `priceAmount` = regular, JSON-LD `price` = promo, `priceValidUntil` = window) —
    e.g. HUGO lamp 49.99→25 (−50%), EGEBY 69.99→35, until 2026-06-21. No fabrication.
  - **Display (product row).** ✅ **Done (Sprint 10.33).** On a verified sale the product row shows the saving
    **both ways** (`−40% · ušteda 20 €` / `−40% · save €20`), a discreet "Na popustu / On sale" badge and the
    struck-through `originalPrice`, plus a "vrijedi do {date}" window. New i18n keys (`results.saleSaving`,
    `results.saleEnds`, `results.regularPrice`) in all 6 languages. Honest guard: the discount is hidden once
    `saleEndsAt` has passed (an expired promo is a false claim → freshness caveat takes over).
  - **Opt-in price-drop alert.** ✅ **Done (Sprint 10.34).** `PriceWatch` entity (externalId + market +
    retailer + email + baseline + threshold + consent + unsubscribe token), `POST /api/price-watch` (explicit
    consent required → 400 otherwise; idempotent per email+product) + `GET /api/price-watch/unsubscribe`,
    a scheduled `PriceWatchRecheckService` that reuses a deterministic `LivePriceProbe` (raw HTTP + JSON-LD
    price), and a `PriceWatchNotifier` **seam** with a log-only default (`@ConditionalOnMissingBean`) — a real
    email provider plugs in later via backend env (owner decision: seam now). Aggressiveness (owner decision):
    ≥5% drop, ≤1 alert/product/cooldown (7 days), never re-notify the same/higher price. The re-check trigger is
    OFF by default (`budgetspace.price-watch.recheck-enabled=false`) so nothing fetches by surprise. GDPR:
    explicit opt-in, one-click unsubscribe, stores only email + product + threshold + consent timestamp. A
    product-row "Watch price" form (email + consent) wires it to the UI.
  - **Monetisation tie-in (value-first):** sale alerts drive return visits + affiliate conversions, but a
    sponsored/affiliate item must still never displace the best organic pick (existing invariant). The alert is
    a genuine user benefit first.
  - *Done when:* ✅ verified sales show the dual % + € saving + badge in plans (10.33), and an opted-in user can
    watch a product and (when the re-check + a provider are enabled) gets a real price-drop notification (10.34)
    — with zero fabricated discounts. **Remaining before live alerts:** wire a real email provider + flip
    `recheck-enabled` on (the seam + logic are done and tested).
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

### Sprint 10.92 — Live AI (Gemini) prompt sweep + confidence fix (current)
- Enabled AI in dev (the key was already in the gitignored `.env`; recreated the container to load it) and ran a
  **17-prompt live Gemini sweep**: HR + EN/DE/FR/ES/IT + NO/SE + tricky + garbage. **Verdict: AI prompt parsing is
  strong** — multilingual room + category parsing correct; correct **non-EUR currency** (NOK 15000 / SEK 12000, no
  conversion); European **"1.500 €" → 1500**; the already-have + exclude logic works ("već imam kauč… bez TV-a" →
  kept rug+lighting, dropped sofa+tv); and **garbage/off-topic** ("asdfghjkl", "pizza", "12345") returns **no
  hallucinated furniture** (empty categories) — exactly the "don't output nonsense" requirement. Plans return 3
  variants × 6 items.
- **Fixed a real issue the sweep exposed:** Gemini sometimes omitted `confidence`, which defaulted to 0.0 →
  falsely triggered the low-confidence "we're unsure" nudge (10.75) on well-parsed plans (~3/17). Strengthened the
  system prompt to ALWAYS return a meaningful confidence (0.8–1.0 for a clear description, < 0.4 for nonsense).
  Re-verified live: clear prompts now **0.8–0.9**, garbage **0.1–0.2** — the nudge fires correctly.
- Known minor edge (not fixed, rare): a foreign-currency-word budget in a different-currency market ("1500 euros"
  in a GBP market) can keep the form's entered budget instead of the stated number.

### Sprint 10.91 — Prompt test: fix European thousands-separator budgets (current)
- Tested more prompts against the **live** parser. With AI off (the prod default), prompts are parsed by the
  deterministic `PlannerIntentExtractor`, not the LLM — so that's the path that actually runs. Found a real bug:
  a budget written in the **localized European format** — "1.500 €" or "1 500 €" — parsed as **500** (the digits
  after the separator), so a user typing exactly the format the UI shows got a plan a third the intended size.
- Fix: `findBudget` now accepts an optional dot/space thousands separator and strips it before parsing
  ("1.500"/"1 500"/"2.000" → 1500/1500/2000); plain "1500" is unchanged. `PlannerIntentExtractorTest` +1.
- (A full **AI/Gemini** prompt sweep still needs Docker up + AI enabled with a key — both are off right now; this
  hardens the rule-based path that handles prompts in the meantime.)

### Sprint 10.90 — Drop the clumsy "decision headline" from results (current)
- Removed the templated headline above the plan ("Za {budget} najisplativije je fokusirati se na {first}; {later}
  može čekati.") — it read awkwardly (it surfaced raw category labels like "sofa / kauč") and added nothing over the
  plan name + worth-it badge + purchase summary already shown right there. Owner call: superfluous, don't show it.
- Removed the `<h3>` in `PlanResults` plus its now-unused builders (`decisionHeadline`/`firstBuyText`/`laterText`);
  the decision-card keeps the badge, the plan name and the summary bullets. Frontend `tsc` build clean.

### Sprint 10.89 — Free→Plus: surface the AI carrot where it bites (current)
- **The AI assistant is the stated Plus carrot, but the funnel never showed it at the value moment.** When a
  Free/guest owner's daily AI allowance is spent, the prompt silently falls back to the rule-based parser — no hint
  that Plus unlocks practically-unlimited AI. Now a contextual nudge appears at exactly that moment.
- **Precise signal (not a false nudge):** added `aiEnabled` to `GET /api/auth/me`
  (`LlmClientFactory.activeClient().isPresent()`), so the frontend distinguishes "AI is on but you're capped"
  (upgrading helps) from "AI is off entirely" (it wouldn't). The nudge shows only when
  `aiEnabled && !analysis.aiUsed && !isPlus` — so with AI off (the current default) it **never** shows.
- Dismissible card reusing the 10.88 upsell pattern; the upgrade CTA was extracted into a shared `upsellCta()`
  (DRY across the save-limit and AI upsells). `AuthMeResponse`/`AuthController` + `client.ts`/`AuthContext` +
  `Planner.tsx` + 2 i18n keys + CSS.
- **Verified:** backend compiles + 249 tests green (AuthController DI + the new DTO field); frontend `tsc` build
  clean. (Docker Desktop was down, so the runtime `/api/auth/me` field + the live nudge — which also needs a Gemini
  key, off in dev — are build-verified, not yet exercised in a browser.)

### Sprint 10.88 — Free→Plus: actionable save-limit upsell (current)
- **The one real conversion trigger was a dead end.** When a Free/guest owner hit the 3-plan save cap, the backend
  returned 402 and the frontend showed a **passive toast** ("…see pricing below") — the user then had to scroll and
  hunt. Now it's an **actionable upsell card** at that exact moment, with a direct CTA branching on state:
  signed-in + billing on → **"Upgrade to Plus"** (starts the real Stripe checkout); guest + billing on → **"Sign in
  for Plus"**; billing off (dev/waitlist) → **"See Plus"** (scrolls to the pricing section). Copy sharpened to name
  the carrots (unlimited saving, AI assistant, price alerts) instead of pointing vaguely "below".
- Reuses the proven `Monetization` checkout pattern (`startCheckout`/`openSignIn`); the upsell clears on a
  successful save. `Planner.tsx` + one i18n key (`plus.seePricing`) + `styles.css` (Plus-accent card).
  **Verified:** `npm run build` (tsc) clean; preview render clean (0 console errors, `#pricing` target present). The
  402-gated flow itself follows the working checkout path (full in-browser run needs the CORS-configured backend).
- (Catalog #2 pass: an ADDE-chair deepening of the thin dining markets returned **0 confirmed / 6 rejected** — ADDE
  is delisted on DE/AT/IT/FI/GB/NO, the verifier refused to fabricate. The easy catalog wins are exhausted; it
  stays at 1987 verified products. Free→Plus is the higher-leverage track.)

### Sprint 10.87 — Catalog depth for the thinnest (room × market) cells (current)
- Filled the worst-stocked cells where a user had almost no choice: **hallway DK/GB/SE (2→5 each)**, **home-office
  DE/SI (3→6), AT (5→8)**, **dining-room DE (4→6), AT (6→7)**.
- **+21 verified IKEA staples** propagated from the existing catalog's global articles (BISSA/STÄLL/MACKAPÄR/TRONES
  shoe storage + NISSEDAL mirror; ALEX desk + drawer unit, MARKUS chair; DANDERYD table, ODGER chair), each in the
  market's **local currency** (BISSA 349 DKK / 599 SEK, STÄLL 769 DKK / 995 SEK, ALEX desk €149–179, DANDERYD
  €129–199, …).
- **Gap-targeted (no duplicates):** the fetch list was computed from the catalog (article × thin-market pairs that
  were missing), and a DB cross-check confirmed 0 of the 21 productUrls already existed. Sourced then
  **independently re-verified** (fetch → second agent re-fetches): **21 confirmed, 1 rejected** (DK TRONES — the
  verifier caught an article-number↔URL inconsistency and refused it). The verifier also corrected GB slugs
  (NISSEDAL→20605485, TRONES→00397307) where the global article maps to a different GB variant.
- New `/catalog/real-ikea-rooms-depth-10-87.json` + seeder entry. `reviewRating/reviewCount=null` (not verified).
  `StoreLinkIntegrity` + runtime tests green; live reseed confirmed the cell depths.

### Sprint 10.86 — Persist the AI usage ledger (caps survive restarts) (current)
- **The cost guardrails were in-memory only** (`AiUsageTracker`), so every restart/redeploy reset the monthly-USD
  wallet cap + per-user daily caps to zero — the wallet was bypassable by restarting mid-month, and DEPLOY.md even
  wrongly claimed the counters survived restarts. Now durable.
- **Write-through + rehydrate-on-boot** (kept the elegant in-memory model — it stays the live, atomic, lock-guarded
  counter source, and all its unit tests are untouched): each REAL (non-fallback) event is written through to a
  new `ai_usage_events` table (entity `AiUsageRecord`, **Flyway V3**), and on startup the tracker rehydrates this
  month's events so the caps pick up where they left off. The write is **best-effort** — a DB hiccup logs a warning
  and never fails the user's request (the in-memory counter already updated). A nightly `@Scheduled` prune drops
  rows past a 45-day retention window (only the current month + today are ever counted), so the table stays bounded
  even at thousands of users.
- Same constructor pattern as `BillingService` (a plain 8-arg test ctor → pure in-memory; an `@Autowired` ctor with
  the repository → durable), so `PromptIntelligenceServiceTest`'s 5 construction sites are unchanged. Atomicity is
  per instance; multi-instance exactness would need a shared store (documented). `AiUsageTrackerTest` +3. Backend
  tests green; prod image boots a fresh DB → Flyway applies V1+V2+V3, `validate` passes, `ai_usage_events` present.

### Sprint 10.85 — Survive load: rate limit + health probe + pool tuning (current)
- **Per-IP rate-limit backstop on `/api/plans/*`** (`RateLimitFilter`). The plan endpoints — especially the
  unauthenticated, ungated `/api/plans/generate-fast` — had NO request-rate limit, so one runaway client (or a
  spike) could saturate the Tomcat threads/CPU and take the instance down for everyone. Fixed-window per IP
  (default 60 req / 10s, configurable), in-memory, runs early (rejects before any work), returns **429 +
  Retry-After**, and stamps the 429 with the CORS allow-origin so the browser surfaces the real status. The bucket
  map is pruned on a schedule + hard-capped so distinct IPs can't grow it unbounded. It's a coarse abuse guard, not
  the AI business limit (that stays in `AiUsageTracker`); per instance — pair with the host/CDN limiter at scale.
- **Health/readiness endpoint** — added `spring-boot-starter-actuator`, exposing **only** `/actuator/health`
  (liveness + readiness, details hidden). The backend image now ships a `HEALTHCHECK` (curl `/actuator/health`),
  and `docker-compose.prod.yml`'s frontend waits for the backend to be **healthy** (not just started). DEPLOY.md
  points the host probe there (was the DB-hitting `/api/auth/me`).
- **DB pool tuning** — HikariCP was on its default (max 10, no acquire timeout). Set `maximum-pool-size`
  (`DB_POOL_MAX_SIZE`, default 15), `minimum-idle`, and a 10s `connection-timeout` so a spike **fails fast** instead
  of piling requests up and hanging.
- Note: the catalog hot path was already cached (PlannerService `allProducts()`, 2s TTL — collapses the N
  full-table loads per plan into ~1 query), so the per-request DB cost was already bounded. `RateLimitFilterTest`
  +4. Backend tests green; verified on the prod image: boots a fresh DB, `/actuator/health` 200, and a flood trips
  429s while the instance stays up.

### Sprint 10.84 — Stripe webhook hardening (idempotency + dunning lifecycle) (current)
- **Idempotency.** Stripe delivers at-least-once and retries on any non-2xx, so the same event arrives more than
  once; the handler applied it every time. Added a `stripe_processed_events` table (event id PK, **Flyway V2**) +
  entity/repo; `handleWebhook` skips an event id it has already applied (recorded in the same transaction, so a
  processing failure rolls back and Stripe's retry reprocesses). Safe to add non-idempotent side-effects later.
- **Dunning lifecycle.** It only handled `checkout.session.completed` + `customer.subscription.deleted`, so a
  failed card (→ `past_due`/`unpaid`/`canceled`) kept full Plus for free the whole dunning window. Now drives the
  entitlement off the live subscription status: `customer.subscription.created|updated` → `active`/`trialing` keeps
  Plus, `past_due`/`unpaid`/`canceled`/`incomplete_expired` downgrades to Free; a recovered card (→ active)
  re-upgrades automatically. `incomplete` is left untouched.
- **Customer fallback.** The subscription id can be null at `checkout.session.completed`; a later lifecycle event
  now resolves the account by **Stripe customer id** (`findByStripeCustomerId`) when the subscription id wasn't
  stored — so a churned user can't get stranded on Plus by a missed id.
- Signature verification (HMAC, constant-time, replay window) was already correct — untouched. `BillingServiceTest`
  +3 (idempotent duplicate, past_due downgrade, active-via-customer-fallback). Backend **242 tests / 0**; prod
  image boots a fresh DB → Flyway applies V1+V2, `validate` passes, `stripe_processed_events` present.

### Sprint 10.83 — Flyway: coherent, durable prod schema management (current)
- **The schema story was incoherent and a launch foot-gun.** Base `ddl-auto=create` (wipes the schema every
  restart); prod default `validate` **wouldn't boot a fresh managed DB** (nothing creates the tables); DEPLOY.md
  told operators to set `update`; no migration tool existed. Three sources disagreed, and `update` can never
  rename/retype/backfill — no safe path once the schema holds paid subscriptions.
- **Flyway now owns the prod schema.** Added `flyway-core` + `flyway-database-postgresql`; a
  **`V1__baseline.sql`** generated from the live Hibernate schema (`pg_dump` of the dev DB → 10 tables, PKs, the
  `app_users.google_sub` unique, the two IDENTITY columns) so a fresh prod boot creates exactly what the entities
  expect. Prod: `ddl-auto=validate` (checks Flyway's schema), `baseline-on-migrate` so an already-`update`-
  bootstrapped DB is adopted as V1 instead of failing. **Dev is unchanged** — Flyway is OFF in the base profile,
  dev keeps the fast `create` + seeder flow.
- From here every schema change ships as a new `V2__*.sql` migration — never `ddl-auto` in prod. DEPLOY.md +
  `.env.example` + `docker-compose.prod.yml` updated (`HIBERNATE_DDL_AUTO=validate`, the misleading "update makes
  counters survive" line removed).
- Verified: backend tests green; Docker image boots against a **fresh empty Postgres** → Flyway applies V1, then
  Hibernate `validate` passes, app starts (proves the baseline matches the entities end-to-end).

### Sprint 10.82 — Production deploy artifact (the #1 launch blocker) (current)
- **The repo had nothing to deploy.** No Dockerfile, no Maven wrapper, no `.dockerignore` — the only runner was
  the dev `docker-compose.override.yml` (`mvn spring-boot:run` + the Vite dev server). A deploy to Railway/Render/
  Fly stalled immediately. Worse, **all prod safety lived only in `application-prod.yml`, gated on
  `SPRING_PROFILES_ACTIVE=prod`** — a var documented nowhere — so a deploy that forgot it silently fell back to the
  base profile: `ddl-auto=create` (wipes the whole schema, incl. accounts + Plus subs, on every restart) and
  PUBLIC admin/catalog-mutation endpoints.
- **`backend/Dockerfile`** — multi-stage (Maven build → slim `eclipse-temurin:21-jre` + fat jar), runs as a
  non-root user, and **bakes in `ENV SPRING_PROFILES_ACTIVE=prod`** so the safe defaults are load-bearing by
  default and can't be forgotten. Verified by a real `docker build` + a prod-profile boot smoke test.
- **`frontend/Dockerfile`** (Vite build → nginx static, SPA fallback + asset caching in `nginx.conf`) replaces the
  dev Vite server. **`backend/mvnw`** (Maven wrapper, `only-script` — no jar committed) makes the build
  reproducible without a host Maven.
- **`.dockerignore`** at the root + `backend/` + `frontend/` so the gitignored `.env` (real secrets) can never be
  baked into an image layer. **`docker-compose.prod.yml`** wires both images + Postgres with the prod profile,
  `restart: unless-stopped`, a DB healthcheck, and **no weak password default** (`POSTGRES_PASSWORD` is required).
- **`backend/.env.example` regenerated** secure-by-default (was stale: `admin-endpoints=true`, openai/anthropic
  instead of Gemini, missing the profile/Stripe/Google/eBay vars). **DEPLOY.md**: `SPRING_PROFILES_ACTIVE=prod` is
  now Gate-0 step 0 + the first row of the env table, plus `BUDGETSPACE_ADMIN_ENDPOINTS_ENABLED=false` and pointers
  to the Dockerfiles/compose. (Follow-ups, runner-ups from the audit: Flyway baseline, Stripe webhook
  idempotency/lifecycle, actuator `/actuator/health` + rate limiting.)

### Sprint 10.81 — GDPR erasure completeness (Art. 17) (current)
- **Account deletion now erases ALL of the user's PII, not just their plans.** `AuthService.deleteAccount`
  previously dropped saved plans + sessions + the account row but left the user's **email** behind in two tables:
  `price_watches` (the price-drop subscriber email — labelled "the only PII") and `plus_interest` (the Plus
  waitlist email). Those rows survived "delete my account" forever — a direct Art. 17 (right-to-erasure) gap for
  an EU-targeted paid app.
- **Fix:** added `deleteByEmailIgnoreCase` (a `@Modifying`/`@Query` bulk delete, matching the existing
  `deleteByOwner` pattern) to `PriceWatchRepository` + `PlusInterestRepository`; `deleteAccount` now calls both
  with the account's email (guarded for a null/blank email) and logs the row counts. An **adversarial completeness
  sweep** confirmed these two + the account row are the **only** entities that store an email — nothing else is
  missed.
- Matched by **email** (case-insensitive), not session id: the browser session id is anonymous and an account
  spans browsers, so the email is the reliable erasure key. `AuthServiceTest`: extended the deletion test to
  assert both purges, + a new test that a null-email account is still fully erased without a blanket delete.
  Backend **239 tests / 0** (was 238).

### Sprint 10.80 — Kitchen depth across 12 thin markets (verified, no fabrication) (current)
- **+33 real IKEA products** filling the kitchen gap in every market that only had ~5–14 items: the two iconic
  carts **RÅSKOG** (art. 30586783) + **NISSAFORS** (art. 40465733) and the **TORNVIKEN** wall shelf (art. 60391661),
  which previously existed only in HR/ES/PT. Now added to **AT, DE, IT, FR, NL, GB, SK, SI, FI, SE, NO, DK** in each
  market's **local currency** (RÅSKOG €29.99–€39.99 / 349 SEK / 349 NOK / 229 DKK / £29, etc.).
- **Sourced then independently re-verified.** A 24-agent workflow fetched each market's IKEA page via the
  number-trick (`/p/-<article>/`), then a **second agent re-fetched every URL** and confirmed name+price (default:
  reject). **33 confirmed, 0 rejected.** The verifier caught and **corrected** wrong slugs the first pass produced
  (FR/FI TORNVIKEN pointed at the 50 cm variant; SE slugs hit a category page; NL/SI 404 → bare-number fallback).
- 3 SI rows kept the global IKEA image without a re-load → marked `imageVerified:false` (price+name verified). All
  rows `reviewRating/reviewCount:null` (not verified). New `/catalog/real-ikea-kitchen-carts-10-80.json` + seeder
  entry. `StoreLinkIntegrity` (+ the per-country runtime tests) validate the file; live-boot reseed confirmed.

### Sprint 10.79 — De-scope home-gym + kitchen-storage sourcing (current)
- **home-gym de-scoped from the UX** (it always came back empty — no verified gym products; IKEA's DAJLIEN range
  is discontinued, Decathlon is feed-blocked). Removed from the **room picker** + the **quick-start templates**;
  removed from the **AI system prompt's roomType list** and the **rule-based extractor's gym→home-gym mapping**,
  so neither path produces it. A gym prompt now falls to a default room (a non-empty plan) instead of an empty
  home-gym. The `RoomType` + backend room→category maps stay (dormant) → one-line re-add once a sports feed exists.
- **Verified kitchen-storage + kitchen sourcing (no fabrication):** +9 real IKEA products, names + EUR prices +
  og:image read off ikea.com/{es,pt} on 2026-06-22 (reviews `null` — not verified):
  - **ES kitchen-storage (was 0):** TORNVIKEN shelf €70, KUNGSFORS shelf €25.99, KUNGSFORS rail+shelf €81.96.
  - **PT kitchen (was thin):** RÅSKOG/NISSAFORS/BEKVÄM/FÖRHÖJA carts + TORNVIKEN/KUNGSFORS storage (€20–139).
  New `/catalog/real-ikea-kitchen-storage-10-79.json` + seeder entry. (Bare-number trick: VADHOLMA didn't resolve
  → skipped; TORNVIKEN/KUNGSFORS/carts did.)
- `PlannerIntentExtractorTest` updated for the de-scope; `StoreLinkIntegrity` validates the new JSON.

### Sprint 10.78 — Instant plan + AI refine (kill the 2s wait) (current)
- Generation no longer makes the user stare at a ~2s LLM spinner. **Two-phase:** the frontend fires a fast
  deterministic plan AND the AI plan in parallel — paints the **rule-based draft in ~0.13s** (spinner off), shows
  a subtle "Refining your plan with AI…" chip, then swaps in the AI-refined plan when it lands (~2s). If the AI
  fails, the draft stays (graceful). Measured: `generate-fast` ~0.13s warm vs `generate` ~2.7s.
- New `POST /api/plans/generate-fast` — rule-based plan only (no AI/LLM, **no auth/tier gating** since no AI
  call). `generatePlanFast` on the client; `runGeneration` refactored into two phases + an `applyResponse` helper
  (only the final AI result counts the generation, fetches the design summary, and feeds the low-confidence
  nudge — the draft has no intentAnalysis, so no premature insight card/nudge).
- Backend 237 tests / 0; frontend build clean; live-boot verified (fast 0.13s, AI 2.7s, `aiUsed:true`).

### Sprint 10.77 — Catalog cleanup + verified ES kitchen sourcing (current)
- **Cleanup — 143 dead "global" rows removed.** `data.sql` seeded 143 unverified sample products (generic
  homepage URLs, Unsplash images, no market, no `sourceReference`) — never plan-eligible, just dead rows.
  Deleted `data.sql`; dev `spring.sql.init.mode` → `never` (prod already used `never`, so prod never had them).
  Catalog is now exactly the verified `RealCatalogSeeder` snapshots: **products 2062 → 1924, 0 null-market rows.**
  *(Caught + fixed a boot break first: a comment-only `data.sql` made Spring throw "'script' must not be null or
  empty" — found via live boot, not tests.)*
- **Verified ES kitchen sourcing (no fabrication).** ES was the thinnest kitchen market (5 items). Added 5 real
  IKEA ES products (RÅSKOG/BEKVÄM/FÖRHÖJA/NISSAFORS carts + RANARP pendant) — name + EUR price + og:image read
  off `ikea.com/es` on 2026-06-21 (same product IDs as the HR catalog; **reviews left null — not verified**).
  **ES kitchen 5 → 10.** New `/catalog/real-ikea-es-kitchen.json` + seeder entry. Pipeline proven: WebFetch
  confirms IKEA name/price/image, and the existing RÅSKOG HR price still matches → the catalog is accurate.
- **Home-gym — NOT sourced (honest finding).** IKEA's DAJLIEN fitness range is a discontinued limited collection
  ("trenutno nema proizvoda" on ikea.com/hr); IKEA isn't a gym retailer. Home-gym needs a sports retailer
  (Decathlon) or to be de-scoped — flagged, not forced. ES kitchen-**storage** is also still thin (follow-up).
- Live-boot verified (1924 products, ES kitchen plan works, aiUsed:true).

### Sprint 10.76 — Performance pass (measured, not guessed) (current)
- **Adversarial perf audit** (render / images / bundle — each finding benchmarked + independently verified): 18
  raw → **4 confirmed worth-fixing, 0 high**. The suspected "re-render storm on every keystroke" was **dismissed
  after measurement** (the re-rendered subtree is ~100-200 elements of µs-cheap work, stable-prop images; no
  user-visible jank) — avoided a premature refactor. Confirmed + fixed:
  - **`formatCurrency` cached** — it built a fresh `Intl.NumberFormat` (~95µs) 40-80× per plan render; now reused
    from a per-`locale|currency` Map. Removes a real per-render CPU cost.
  - **React vendor chunk** — `manualChunks` splits React/ReactDOM (~45KB gz) into a stable `vendor` chunk so it
    stays cached across the frequent app-only deploys. App chunk **91KB → 43KB gz** (+ 45KB now-cacheable vendor).
  - **Legal modals lazy-loaded** — `LegalModal` + `legal.ts` (~3.3KB gz) load only on a footer click, off the
    critical path.
  - **Fallback product images 900px → 240px** (Unsplash `w`/`q`) — 5–10× fewer bytes per placeholder (rendered
    into ≤112px boxes; already `loading="lazy"`).
  - *Skipped with reason:* code-splitting `PlanResults` — it renders the initial empty-state on load, so deferring
    it needs a refactor not worth the optimistic ~20KB on a non-critical 91KB bundle.
- Backend latency is fine (measured): `/design` 0.02s, `/me` 0.01s; `/generate` ~2s is the inherent Gemini call
  (covered by the loading state), not jank. Frontend build clean; lazy legal modal verified in-browser.

### Sprint 10.75 — Low-confidence nudge (C) + multilingual room parsing (current)
- **C — low-confidence nudge:** when the AI ran but couldn't tell what was asked (garbage / off-topic / very-vague
  typed prompt → `confidence < 0.4`), the plan **still renders** (never blocks the funnel) but a friendly,
  non-blocking note nudges the user to describe a room + budget — instead of silently presenting a guessed
  living-room as if it were the request. The misleading "AI understood" card is suppressed in that case.
  Frontend-only (confidence is already in the response; gated on `aiUsed` + a non-empty typed prompt).
- **Multilingual room parsing fixed:** a broad sweep (15 markets × 3 rooms, each in the market's own language)
  found the AI was HR/EN-centric and misparsed foreign room words to living-room (FR "chambre" → living-room even
  in a full sentence; terse GB "kitchen" → living-room). The system prompt now states the input may be in ANY
  supported language and MUST map the named room to the canonical value (cuisine/Küche/kuhinja → kitchen;
  chambre/Schlafzimmer → bedroom; …), never substituting living-room for a named room. **Re-sweep: 45/45 rooms
  correct (was 33/45).**
- **Robustness re-confirmed** after the prompt change: the 17-case adversarial sweep stays all-200, no
  crash/leak/fabrication; garbage still degrades to a safe default (now surfaced honestly via the C nudge).
- Backend 237 tests / 0; frontend build clean; C verified to render (app loads clean, no console errors).

### Sprint 10.74 — Currency-correct budgets + prompt robustness audit (current)
- **Currency bug fixed (non-EUR markets)** — two issues a live sweep surfaced:
  (1) the AI was told the budget is in "EUR", so it **converted** foreign amounts (NOK 15000 → returned 1500 →
  the plan targeted ~1400 NOK = 2 items instead of the real 15000 NOK). Fixed: the prompt now states the market's
  currency and tells the model to return the budget **in that currency, no conversion**.
  (2) the budget clamp was a hardcoded EUR-centric `[100, 9000]`, capping legitimate NOK/SEK/DKK (and high
  EUR/GBP) budgets. Fixed: `Markets.budgetCeiling(currency)` — a per-currency ceiling (≈ €9000 of real value);
  applied in the AI path (`sanitize` + `toPlannerInput`) and the rule-based extractor.
- **Adversarial prompt audit (17 cases)** — empty, gibberish, off-topic, prompt-injection, SQL/HTML, emoji,
  extreme/negative budgets, mixed-language, very-long, rude → **ALL** returned HTTP 200 with a valid plan of real
  catalog products: **no crash, no fabrication, no leak** (injection never exposed the system prompt). The
  deterministic planner + AI sanitisation (unknown rooms/categories/retailers dropped) hold. Garbage degrades to a
  sensible default room rather than breaking.
- Backend 237 tests / 0 (+2 `MarketsTest`: per-currency ceiling + `currencyFor`); currency re-sweep confirms
  NOK/SEK/DKK now parse the real amount. AI was re-verified live across all 15 markets earlier this session.

### Sprint 10.73 — Cancel-on-delete + deploy checklist (current)
- **Cancel-on-delete:** `AuthController.deleteAccount` now cancels any live Stripe subscription (best-effort,
  never blocks the erasure) BEFORE deleting the account — so a deleted account is never billed again. New
  `BillingService.cancelSubscriptionQuietly` (DELETE `/v1/subscriptions/<id>`) + a `StripeHttp.delete`; closes the
  10.72 gap. Backend 235 tests / 0 (+3: cancel hits Stripe, is best-effort/never-throws, ignores a missing sub).
- **`DEPLOY.md`** — an ordered, provider-agnostic hosting checklist (no secret values): rotate secrets, persistent
  Postgres + `HIBERNATE_DDL_AUTO=update` (the real fix for the restart-wipe), HTTPS + `cookie-secure`, prod domain
  on Google OAuth/CORS/`VITE_API_BASE_URL`, Stripe **live** keys + webhook (`STRIPE_WEBHOOK_SECRET`), backups +
  monitoring, and a post-deploy smoke test. Env-var reference table marks which are secret.
- **AI verified live across all 15 markets** (HR/SI/AT/DE/IT/FI/FR/NL/SK/ES/PT/NO/SE/DK/GB): every one →
  `aiUsed:true, source:gemini`, with the room + budget correctly parsed from a prompt in that market's own
  language. (A first bash/curl sweep showed false "errors" for diacritic prompts — a Windows-shell UTF-8 artifact,
  not an app bug; confirmed clean via a Node/fetch re-run.)

### Sprint 10.72 — Legal/GDPR scaffold + account deletion (current)
- **GDPR "right to be forgotten":** `DELETE /api/auth/account` (authenticated; 401 for guests) erases the account
  and ALL its data — saved plans (owned under `user:<id>`), every session, and the account row — then clears the
  cookie. Self-scoped (no target-id param → no IDOR; a user can only delete their own account). Frontend: a
  "Delete account" link in the footer (signed-in only) → confirm dialog → `AuthContext` drops the local user.
  **(Gap, tracked):** a live Stripe subscription is NOT yet cancelled on deletion — billing is test-mode/dormant;
  cancel before going live so a deleted account stops being billed.
- **Legal scaffold:** Privacy / Terms / Impressum as footer-opened modals (the app has no router), content in a new
  `legal.ts` (HR + EN, English fallback for other locales) tailored to the real data flows — Google profile, saved
  plans, Stripe (we never see card data), the AI provider (prompts), eBay (listings), the single
  strictly-necessary auth cookie. Each doc carries a "template — review with a lawyer" disclaimer; the Impressum
  has `[fill-in]` placeholders (legally required in HR/EU).
- Backend 232 tests / 0 failures (+2: deleteAccount erases plans+sessions+user; no-op for null); frontend build
  clean; legal modal verified in-browser; live-boot verified (`DELETE` → 401 without auth).
- **Next (before public launch):** rotate shared secrets, hosting + persistent DB (`HIBERNATE_DDL_AUTO` +
  migrations — also makes AI counters/accounts/subs durable), prod Stripe webhook + cancel-on-delete, monitoring +
  backups.

### Sprint 10.71 — Gate DesignAssistant + persistence finding (current)
- **`DesignAssistantService` (the separate Anthropic AI path) now shares the same guardrails** as the prompt path:
  `PlanController.design` resolves the caller (a shared `resolveCaller` helper with `/generate`), and the service
  calls `AiUsageTracker.tryAcquire`/`complete` so design-summary calls **count toward the per-tier daily cap +
  the monthly USD budget** instead of bypassing them. Off by default still; this closes the last AI cost bypass
  the 10.70 review flagged. (USD estimate uses the cheap-provider rates — tune when actually enabling Anthropic;
  the per-tier **call** cap is the binding control.) Frontend sends the session header on `/api/plans/design` too.
- **Finding (re: "persist AI counters"):** the AI counter resetting on restart is the *same* problem as
  `ddl-auto: create` wiping the **whole** DB (accounts, subscriptions, saved plans) on every restart — see the
  pre-launch roadmap items (c)/(2). Persisting only the AI table while accounts vanish would be theater; the real
  fix is the persistent-DB posture (managed Postgres + `HIBERNATE_DDL_AUTO=validate/update` + migrations), which
  lands with hosting and makes **everything** — including the AI counters once DB-backed — durable. So the
  counter persistence is bundled there, not done in isolation here.
- Backend 230 tests / 0 failures; frontend build clean; live-boot verified.

### Sprint 10.70 — Tiers live: per-tier AI caps + Pro "coming soon" (current)
- **AI is now gated by subscription tier, per user, per day** — the core of the value ladder. `PlanController`
  resolves the caller (owner key `user:<id>` signed-in / `guest:<browserId>`) and tier via `AuthService`, and
  `AiUsageTracker` enforces a **per-owner daily allowance per tier**: **Guest 3 · Free 10 · Plus 100 · Pro 500**
  (all env-configurable under `budgetspace.ai.daily-per-user`). Over the cap → the deterministic rule-based plan
  (never an error). Free is generous enough to "wow" without bouncing; Plus feels unlimited for a human; the
  **global monthly-USD budget stays the wallet's hard stop** regardless of tier.
- Layered guardrails, widest→narrowest: monthly USD budget (wallet) → global daily backstop → per-user/per-tier
  daily cap (product control). `AiUsageEvent` now carries `ownerKey` + `tier` (replaced the raw session id); the
  in-memory counters reset on restart (fine pre-launch; **persist before real scale** — see Placeholders).
- **Third tier (Pro €12.99) shown as "coming soon"** — anchors Plus and collects demand (a "Notify me" records
  interest, no checkout). Three-column pricing (`.three-tier`, stacks <900px); the existing dead Pro i18n keys
  filled in. Plan stays a string, so "PRO" needed no migration; `isPlus`/share now treat PRO as paid.
- **Plus removes the share-card watermark** ("Built with BudgetSpace" stays for Free = organic-growth tag; Plus/Pro
  share clean). Free pricing copy now advertises the AI taste.
- Frontend build clean; pricing three-tier verified in-browser (3 columns desktop, stacked mobile, no console
  errors). Live-boot verified end-to-end: guest `/api/plans/generate` → 200, `aiUsed:true`, `source:gemini` (the
  new `PlanController`→`AuthService`→`AiUsageTracker` path works and the AI actually runs).
- **Adversarial review (4 dimensions → each finding independently verified): 0 blockers, 2 majors — both fixed.**
  (1) **TOCTOU race** — concurrent same-owner requests could overshoot the per-tier cap (the check and the record
  were separate locks with the LLM call between). Fixed with an **atomic per-owner in-flight reservation**
  (`tryAcquire`/`complete`); added a concurrency test proving the cap holds under a 6-way burst. (2) **Eviction
  undercount** — unbounded fallback events could evict today's real events from the 5000-slot log and soften the
  caps; fixed by **not retaining fallback events** (they count toward nothing). Also hardened: fail-closed on a
  blank owner key, explicit GUEST case, dedicated Pro "notify" copy, removed dead `.two-tier` CSS.
- **Deferred (tracked):** `DesignAssistantService` (a separate Anthropic path) still bypasses the caps — but it's
  **off by default**, pre-existing (10.8), and now flagged as its own task; route it through `AiUsageTracker`
  before enabling it.
- **Next:** the production-readiness pass (persist AI counters, prod webhook secret, rotate shared secrets, legal
  pages + GDPR, hosting + backups + monitoring).

### Sprint 10.69 — Stripe checkout (real Plus billing) (current)
- Real Plus subscription via **Stripe (TEST mode), hosted Checkout** — backend creates the session, the frontend
  redirects to Stripe's page (no Stripe.js, no publishable key in the bundle). **Raw JDK HTTP, no Stripe SDK**
  (consistent with the eBay/LLM clients).
- Flow: a signed-in user → `POST /api/billing/checkout` (subscription session for the Plus price, tagged
  `client_reference_id=userId`) → Stripe → back to `?plus=success&session_id=…` → `AuthContext` →
  `POST /api/billing/confirm` → the backend **re-fetches the session from Stripe** and upgrades to PLUS only when
  it's **paid AND owned by the authenticated account** (never a client-supplied id). The **webhook**
  (`POST /api/billing/webhook`, HMAC-SHA256 verified, replay window, constant-time, dormant without
  `STRIPE_WEBHOOK_SECRET`) is the production path (upgrade on `checkout.session.completed`, downgrade on
  `customer.subscription.deleted`); dev uses confirm-on-redirect.
- `AppUser.stripeCustomerId/SubscriptionId`; `/api/auth/me` now returns `billingEnabled` so the Plus CTA is a real
  "Upgrade" when Stripe is on, else the waitlist; "Welcome to Plus 🎉" on return. Secret key backend-only
  (gitignored `.env`, value-free in compose). Price `price_1TkM40…` (€/mo).
- **Adversarial review: 0 blockers/0 majors** — webhook signature, the confirm owner-check, billing auth (401 for
  guests), open-redirect/SSRF, and secret handling all verified clean; fixed 1 nit (dead code). **Backend 227
  tests, 0 failures** (incl. `BillingServiceTest`: signature forgery/replay + owner-check + checkout form);
  frontend build clean.
- **Next:** test live (test card 4242…); then gate the **AI assistant** behind Plus, wire the webhook in prod
  (Stripe CLI / dashboard → `STRIPE_WEBHOOK_SECRET`), and a customer-portal "manage subscription" link.

### Sprint 10.68 — Plus scaffold (monetization) (current)
- Monetization, honest + lean (no Stripe yet). **Entitlement:** `AppUser.plan` ("FREE"/"PLUS", default FREE),
  exposed via `/api/auth/me`. Only a signed-in account can be Plus; guests are always Free (forge-proof — reuses
  the `user:`/`guest:` namespace).
- **One real gate that works today:** Free saves up to **3 plans** (`budgetspace.plus.free-saved-limit`), Plus =
  unlimited. The cap is enforced server-side in `SavedPlanService.save` (new `PlanLimitReachedException → 402`);
  the frontend catches the 402 and shows a discreet Plus upsell, and **save + the share growth-loop degrade
  gracefully** (no crash, no broken /plan/<id> link).
- **Real Free/Plus pricing UI** (€5.99/mo) replacing the old Free/Pro/Pro+ pilot cards, plus an **honest waitlist**
  (no fake checkout) that records a **willingness-to-pay signal** — `POST /api/events/plus-interest` (optional
  email for the launch list; email value never logged).
- An **adversarial review** confirmed 0 blockers/0 majors (cap off-by-one correct, entitlement forge-proof,
  save/share degradation sound); fixed 2 nits (truncate plus-interest input vs column overflow; generic upsell
  copy). **Backend 220 tests, 0 failures; frontend build clean.** Verified live: new schema booted clean,
  `/api/events/plus-interest` → 202.
- **Stripe (real checkout + the AI/export gating) is the next monetization step** — needs a Stripe account +
  test keys; the entitlement + gating + UI are ready for it to plug into.
- Also: Gemini default model → `gemini-2.5-flash` (`gemini-2.0-flash` 404s on the owner's key).

### Sprint 10.67 — security hardening (from the adversarial audit) (current)
- Ran a **12-dimension adversarial security audit** (multi-agent: review → independently verify each finding).
  **47 raw findings → 9 confirmed, 0 critical, 0 exploitable app vulns.** Core security held: auth, IDOR/owner
  isolation (the 10.63 namespace fix), SQL/JPQL (all parameterised), XSS (React auto-escape, no
  dangerouslySetInnerHTML), SSRF (all outbound hosts hardcoded), secrets (.env gitignored, keys backend-only),
  CORS (explicit allow-list) — all verified clean. The 38 dropped findings were false-positives or accepted design.
- **Fixed now (cheap + real):** (1) prod session cookie `Secure` flag (application-prod.yml `cookie-secure:true`;
  base dev default stays false for HTTP localhost); (2) prod `ddl-auto:validate` + `sql.init.mode:never` (was
  `create` → wiped all data on every prod restart); (3) share-plan id → **128-bit CSPRNG** (was a 40-bit UUID
  prefix; the /plan/<id> link is an open capability); (4) prompt length cap (4000) to bound LLM tokens / body
  abuse; (5) stop logging the price-watch **unsubscribe token** (a bearer capability); (6) baseline
  **SecurityHeadersFilter** (nosniff, X-Frame-Options DENY, Referrer-Policy, HSTS on HTTPS). **Backend 218 tests, 0
  failures.**
- **Deferred (do before PUBLIC launch / when AI goes multi-user — not urgent pre-traction):**
  (a) ✅ **DONE 10.70** — per-account/per-tier AI quota keyed on `resolveOwnerKey` (user:/guest:), with an atomic
  per-owner reservation (TOCTOU-safe). (Caps are still in-memory → reset on restart; persist with the DB switch,
  item (c).) (b) **Rate limiting** (none today) on `/api/auth/google`, `/api/plans/generate`, `/api/price-watch`
  — a lightweight per-IP/owner limiter (price-watch is an unauthenticated DB write → row-flooding). (c)
  **Flyway/Liquibase** versioned migrations + `HIBERNATE_DDL_AUTO=validate/update` (the real fix behind ddl-auto;
  this is also what makes the AI counters + accounts + subs actually persist across restarts). (d) **HTTPS + CSP**
  at the reverse proxy (CSP must allow-list Google Identity Services + retailer image hosts). (e) ✅ **DONE 10.71**
  — `DesignAssistantService` now routes through `AiUsageTracker` (shares the per-tier cap + monthly budget). (f)
  Real `DATABASE_PASSWORD` in prod (the committed `budgetspace/budgetspace` is dev-compose only).

### Sprint 10.66 — Gemini AI provider (the "Plus" carrot) (current)
- Monetization direction (this session): affiliate is the long-term money but **gated behind traffic** (won't get
  approved pre-traction); the core is deterministic so AI cost is **not** the risk the way it is for AI-native
  products. The smart early model = generous Free (≈€0 to serve) + a thin **Plus (€5.99/mo)** whose real carrot is
  the **AI assistant**. The AI assistant needs a cheap provider → **Gemini Flash** (cheapest + free tier).
- Added `GeminiLlmClient` to the existing provider-agnostic LLM layer (raw JDK HTTP, no SDK — like OpenAI/Anthropic):
  Gemini's shape (top-level `systemInstruction`, `contents`, JSON via `responseMimeType`, `candidates`/
  `usageMetadata`); key in the `x-goog-api-key` header (never the URL/logs). `LlmProvider.GEMINI` + `LlmProperties`
  (key + `gemini-2.0-flash` default). Pure `buildBody`/`parseCompletion` are unit-tested (5 tests) without a
  network. **Backend 218 tests, 0 failures.**
- **Still OFF by default** (`aiUsable()` false → rule-based). Flip on via the gitignored `.env`
  (`BUDGETSPACE_AI_ENABLED=true` + `BUDGETSPACE_LLM_PROVIDER=gemini` + `GEMINI_API_KEY`); hard-capped by
  `AiUsageTracker`, rule-based fallback on every failure. The owner's earlier "defer AI until EU catalog" hold was
  lifted because Gemini's free tier removes the cost-of-testing concern. (Plus-gating of AI is a later sprint.)

### Sprint 10.65 — missing saved plan → 404, not 500 (current)
- Bug (owner-spotted in the logs): opening a shared `/plan/<id>` link whose plan no longer exists threw
  `NoSuchElementException` → `GlobalExceptionHandler` mapped it to **500 + a full stack trace**. Common in dev
  because `ddl-auto=create` wipes `saved_plans` on every restart, so every old link 404s.
- Fix: a dedicated `SavedPlanNotFoundException` (extends `NoSuchElementException`, so existing call sites/tests
  are unaffected) thrown by `SavedPlanService.findById`/`setFavorite`; `GlobalExceptionHandler` maps it to a
  clean **404** ("Plan nije pronađen.") logged quietly — a stale/shared link is an expected client situation, not
  a server fault. New test `findByIdThrowsNotFoundForAMissingPlan`; favorite owner-check now asserts the specific
  type. **Backend 213 tests, 0 failures.** (The underlying wipe-on-restart is the known `ddl-auto=create` prod
  blocker — Flyway/Liquibase before deploy.)

### Sprint 10.64 — eBay "Rabljeno" → live, no-persist (compliance) (current)
- The owner's eBay PRODUCTION app declares *"we do not persist eBay data — responses are used only transiently."*
  But the Sprint 10.51 `EbayBrowseFeed` was a `MarketplaceFeed` whose rows the startup importer **wrote into the
  `products` table** and the planner served from the DB — a direct conflict (and against eBay's ToS). An
  adversarial review + compliance check caught it before the keys were ever wired.
- **Rework:** `EbayBrowseFeed` is now a **live, request-time `@Service`** (removed from the importer bean list).
  The planner calls `findUsedFurniture(market)` while building a plan; the proven OAuth + Browse search + mapping
  is reused, but results map to **transient `Product`s, returned in that one response and NEVER persisted** — a
  short (10 min) in-memory cache only. The same second-hand filters (room/condition/link/freshness) + sold guard
  still apply; used items still never enter a plan or total. The `liveUsedListings…` test proves the whole live
  path end-to-end (offline, via an injected transport). **Backend 212 tests, 0 failures.**
- **PRODUCTION keys wired** via the gitignored `.env` (Cert ID is a real secret — never committed; value-free
  reference in `docker-compose.override.yml`). Live for DE/IT/AT/FR/NL/ES/GB; dormant when blank. Rotate the Cert
  ID (shared in chat); possibly tune the furniture category id (3197) per market. See `memory/ebay-no-persist.md`.

### Sprint 10.63 — real Google sign-in + sessions before the app (current)
- Owner: "Google prijava prije ulaska u app, čuvaj sesiju, odlogiraj nakon nekog vremena." Replaced the honest
  disabled placeholder with **real Google Sign-In** + a server session lifecycle.
- **Backend (211 tests, 0 failures — +17 new):** `GoogleTokenVerifier` verifies the Google ID token **locally**
  (Nimbus, RS256 against Google's JWKS; audience = our public client id; issuer/expiry checked) — **no client
  secret** (ID-token flow, not the code flow). `AppUser` (by Google sub) + `AuthSession` (opaque token, absolute
  `expiresAt` + sliding `lastSeenAt`) → **auto-logout** on TTL/idle. `AuthService` upserts the account, mints a
  session, and **migrates the guest's saved plans** onto the account on first sign-in. `AuthController`:
  `POST /api/auth/google` (sets an HttpOnly `SameSite=Lax` cookie), `GET /api/auth/me` (who + the public client
  id), `POST /api/auth/logout`. CORS now `allowCredentials(true)`; saved-plan ownership resolves to the account
  when signed in, else the guest session (back-compat). Config: `BUDGETSPACE_GOOGLE_CLIENTID` (blank → dormant),
  `session.ttl-days` (30), `session.idle-days` (7).
- **Frontend:** `AuthProvider` (loads `/api/auth/me`), a premium **sign-in front door** shown on entry (with a
  **"Nastavi kao gost"** escape; **shared `/plan/<id>` links bypass it** so the share loop never hits a wall),
  the real **Google Identity Services** button, header sign-in/out + avatar, and the planner account strip + saved
  inbox now reflect real account state (saved plans refetch on sign-in to show the migrated ones). New i18n (hr+en)
  + CSS. **Frontend build clean.**
- **Dormant seam preserved:** with no client id, the Google button stays honestly disabled and the app is
  guest-only — no fake auth. The public client id is served by the backend (`/api/auth/me`), one source of truth.

### Sprint 10.62 — calmer results (UX simplification) (current)
- Owner: "čim uđe i izgenerira plan vidi dosta toga i svačega." After a plan, `PlanResults` stacked ~11 full
  sections at once. Restructured into **progressive disclosure — nothing removed**, the default view is now just
  the **verdict + products + 3 actions**.
- **Per-product row:** only **"Otvori u trgovini"** stays visible (now the filled primary); Reviews / Promijeni /
  Zadrži / Prati cijenu hide behind one **"Više opcija"** toggle (new `actionsProductId` state + `toggleActions`).
- **One collapsed `<details>` "Više o ovom planu"** now holds the supporting detail: budget breakdown, shopping-
  by-store, version comparison, full plan details, "što smo razumjeli". Rabljeno / Share / Feedback stay below.
- Removed only the redundant "najvažnije u planu" strip (it duplicated the products list's first step).
- **Adversarial multi-agent review** (4 dimensions + double-verify) caught + fixed: missing `aria-expanded` on the
  Change / dislike toggles; an iterative-replace collapse (split the reset effect so an in-place swap keeps the
  row open); a locked-item replacement menu staying live (`!locked` gate); two mobile `@media` parity gaps for
  the new panel + secondary actions. New i18n (hr+en) + CSS. **Frontend build clean (tsc strict + vite).**

### Sprint 10.61 — Saved Spaces (multiple rooms per home) (current)
- Retention feature: a **"space"** (e.g. "Moj dom") groups a session's saved room-plans, so a user can design a
  living room → bedroom → office for the **same home** and come back to keep going ("continue designing").
- **Backend:** `SavedPlan.spaceName` (nullable) threaded through request/response/save (like `session_id`);
  `SavedPlanServiceTest` asserts it's stamped + returned. **Backend 194 tests, 0 failures.**
- **Frontend:** `savePlan` sends the active space (default "Moj dom") so each room joins it; **"Moji planovi" now
  GROUPS plans by space**, with an active-space chip row + a "+ novi prostor" input to create/switch, and a
  per-space **"Nastavi slagati"** button that re-activates the space + scrolls to the form for the next room.
  Opening a saved plan adopts its space; the save notice says which space it landed in. New i18n (hr+en) + CSS.
  Frontend build clean.

### Sprint 10.60 — social share card (growth loop) (current)
- After a plan, a **"Podijeli svoj plan"** card shows a clean shareable summary ("Moj dnevni boravak do 1500 €:
  kauč 349 €, … · ukupno 827 € · ostaje 673 €. Složeno u BudgetSpace.") + share buttons: **native share** (when
  available), **WhatsApp, Reddit, X, copy**. Reuses `onSavePlan` to mint a shareable `/plan/<id>` link once
  (cached, so repeated shares don't re-save). Frontend-only. New i18n (hr+en) + premium CSS. Build clean.

### Sprint 10.59 — budget breakdown (where your money goes) (current)
- Makes the planner's existing budget allocation **visible**: a stacked "Kamo ide budžet / Where your budget
  goes" bar (share per category) with the **remaining (or over) budget highlighted**, placed right under the
  decision card. Legend lists the top 6 categories with € + %. Frontend-only — uses plan data, no backend
  change. New i18n (hr+en) + premium bar/legend CSS. Frontend build clean.

### Sprint 10.58 — vibe copy in a human voice + moodboard placeholder + placeholders registry (current)
- **Vibe copy → less AI, more human:** rewrote the 6 vibe descriptors + the intro so each paints a relatable
  room a person recognises ("Svijetlo drvo, mekani tekstil i puno zraka — toplo, a uredno." / "Metal, tamni
  tonovi i karakter starog potkrovlja." / "…u koje se poželiš zavaliti."), not a list of attributes. Descriptors
  are display-only, so the verified style-pure prompts are untouched (vibe→style mapping unchanged).
- **Moodboard upload placeholder:** an honest disabled "Učitaj moodboard · uskoro" card under the vibe picker
  (no fake upload, no fake state) — it needs a vision/AI layer, tracked in the placeholders registry.
- **Placeholders registry:** added a top-of-TASKS section listing every intentional placeholder/dormant seam
  (eBay feed, per-country marketplaces, Google login, moodboard, AI layer, price-drop email, blocked-retailer
  feeds, affiliate/sponsored, eBay seller images, prod DB ddl-auto) with what activates each — so none is shipped
  as-is or forgotten. Frontend build clean.

### Sprint 10.57 — "Choose a vibe" style picker (current)
- Owner: users who can't describe their style should be able to pick one. Built a premium one-tap **vibe picker**
  in the planner form (right after the prompt): **Scandinavian / Japandi / Minimalist / Industrial / Warm modern /
  Luxury hotel** — each a card with a 3-tone palette swatch + a one-line descriptor.
- Each vibe is a **style overlay over the existing engine (no backend change):** it sets the canonical style +
  colour/material preferences the planner already scores on, plus a **STYLE-PURE prompt**. The prompt carries only
  a style cue (no room/colour/material/budget words), so the rule-based extractor maps it to exactly the intended
  style AND preserves the vibe's colours/materials; the user's room/budget/stores are kept. Mappings **verified
  LIVE** against the running extractor (HR + EN) and locked by `VibeStylePresetTest`.
- i18n: 21 new hr+en keys (heading/intro + 6×label/desc/prompt); premium card + swatch CSS. **Verified live**: the
  panel renders all 6 cards; clicking one updates the prompt + marks the card active; zero console errors.
  **Backend 193 tests, 0 failures; frontend build clean.**

### Sprint 10.56 — UK depth + live full-stack smoke test (current)
- **UK depth:** +31 web-verified IKEA GB products (real name + GBP + og:image on ikea.com/gb/en) → GB catalog
  **48 → 79**, every thin cell now ≥3 (sofa/bed/mattress/table/rug/tv-unit/dining-chair 1→4; wardrobe/dresser/
  decor/kitchen-storage 2→4). Same no-fabrication recipe (parallel discover+verify workflow → deterministic
  number-trick URLs → dedup vs existing). SONGESAND bed was on sale → kept the current £129 only (no fabricated
  discount window). `GbCatalogRuntimeTest` + `StoreLinkIntegrityTest` green; **backend 191 tests, 0 failures.**
- **Live full-stack smoke test** (the real docker stack — Postgres + Spring on 8090 + Vite frontend): restarted
  the backend (devtools-restart is off by design → a manual restart recompiles the new code + re-seeds via
  `ddl-auto=create`). Verified end-to-end: a GB request returns a **non-partial plan of REAL GB IKEA products in
  GBP** (KIVIK £599, LACK TV bench £40, STOENSE rug £79…), `secondHandSuggestions` empty (no eBay key yet),
  currency GBP.
- **Swap verified (owner's question — "replace sofa only, keep the rest, coherent"):** `/api/plans/replace`
  changes only the targeted item and keeps the rest **byte-identical** (confirmed all 5 non-sofa items unchanged
  after a sofa replace). When no better option exists it's an honest no-op (pre-depth, with 1 sofa, "cheaper"
  returned the same plan instead of inventing one); depth now provides real swap alternatives. The replacement is
  scored on same category + room + style + budget fit, so it stays coherent.

### Sprint 10.55 — United Kingdom: 15th market + verified IKEA GB catalog (current)
- Owner asked for a UK market + UK catalog. Built it the no-fabrication way: every product web-verified on
  ikea.com/gb/en (English name + GBP price + og:image) — never invented.
- **Catalog:** `real-ikea-gb-rooms.json` — **48 IKEA GB products** across all 7 rooms, **every planner cell ≥1**
  (sofa/bed/mattress/tv-unit/desk/chair/dining-table/dining-chair + storage/lighting/rug/decor/…). Ported the
  IKEA ES set's global article numbers to `/gb/en/` via a parallel verification workflow (10 agents, WebFetch),
  then hardened: **deterministic number-trick URLs** (`https://www.ikea.com/gb/en/p/-<article>/`, each verified to
  resolve to a live product) instead of model-extracted slugs; the 4 combination-article URLs spot-checked LIVE;
  **10 exact duplicates removed**; ÅFJÄLL (number-trick → category page) dropped and replaced with a verified
  ÅBYGDA mattress (£139). Prices spot-checked on the live pages (KIVIK £599, ALEX desk £129, …).
- **Wiring:** GB → GBP/en-GB in `Markets.java` + `markets.ts` (`available:true`) + `retailersByMarket`
  (IKEA-only — no JYSK in the UK) + GB cities + prompt detection (london/uk/britain/…). **eBay GB** added to the
  Browse feed's `SUPPORTED_MARKETS`, so "Rabljeno" covers the UK the moment the eBay key is set. Catalog
  registered in `RealCatalogSeeder`.
- **Tests:** `GbCatalogRuntimeTest` — imports cleanly, every row market=GB/IKEA/planner-eligible with a verified
  image + `www.ikea.com/gb` URL, unique ids/links, all rooms + core categories covered, a GB living-room plan is
  non-partial. **Backend 191 tests, 0 failures.** Frontend build clean; 🇬🇧 United Kingdom verified live in the picker.
- JYSK has no UK stores → IKEA-only for now; depth (more sofas/beds + UK non-IKEA retailers) is a later sweep.

### Sprint 10.54 — plan feedback that acts (current)
- Owner asked: when the user picks "Preskupo" on "Je li ovaj plan dobar?", what happens? **Before:** nothing
  useful — it was recorded to the DB (and never read) + a generic thank-you. Pure telemetry.
- **Now** each rating maps to the existing quick action that fixes it, offered as a **one-click CTA** after the
  thanks (never auto-regenerated, so a user mid-evaluation is never surprised): too-expensive → cheaper,
  wrong-style → nicer, too-many-stores → fewer stores; "useful" just says thanks. Reuses the proven regeneration
  path — no new endpoints. A stale CTA can't linger: feedback clears when a fresh plan set arrives.
- **Adversarially reviewed** (2 independent reviewers): security **PASS** (no way to read/modify another user's
  plans; share-link stays open by id; no session leak), correctness **clean** (all i18n keys present, mapping +
  type narrowing correct, no regression). Frontend build clean.

### Sprint 10.53 — saved plans become *yours* (privacy fix) + Google-login seam (current)
- Owner asked "does 'Spremi u moje planove' actually save anywhere?" — it did, but to a **shared, global table**:
  `listSavedPlans` returned EVERY visitor's plans, so "Moji planovi" showed strangers' plans. A real privacy leak.
- **Fixed (session-scoping):** `SavedPlan` now carries a `session_id` (the existing `X-BudgetSpace-Session`). The
  inbox query is scoped (`findBySessionIdOrderBy…`); the controller reads the header and threads it through save /
  list / favorite. Only the owner can toggle favorite (a non-owner is treated as not-found, so ownership never
  leaks). The frontend sends the header on save / list / favorite. **Share-by-link stays OPEN by id** (the id is
  the capability) so a shared plan still opens for the recipient. `SavedPlanServiceTest` proves: save stamps the
  owner; the inbox returns only the caller's plans (empty for no session); a shared link opens by id; only the
  owner can favorite. **Backend 189 tests, 0 failures.**
- **Google-login seam (honest placeholder):** a discreet "Tvoji planovi" strip with a *disabled* "Prijava s
  Google-om · uskoro" button + tooltip ("plans will be tied to your Google account, not this browser"). **No fake
  logged-in state, no fake OAuth, no localStorage flags** — just the seam + honest copy; real OAuth lands later.
  Verified live: strip renders, correct copy, button disabled, zero console errors. Frontend build clean.
- Note: `ddl-auto=create` rebuilds the dev schema, so the new column needs no migration now; the prod move to
  Flyway adds it as a versioned migration (already a tracked prod blocker).

### Sprint 10.52 — "Rabljeno" frontend: the separate second-hand section (current)
- The visible half of 10.51: renders `PlanGenerationResponse.secondHandSuggestions` in a distinct "Rabljeno"
  block under the new-retail plan, **never mixed into a total**.
- **Threaded** `secondHandSuggestions`: `api/client` response type → `Planner` state (cleared on every path —
  reset, saved-plan, shared-plan) → `PlanResults` prop. `Product` type gained
  `secondHand`/`conditionLabel`/`sellerLocation` (mirrors the backend DTO).
- **New `SecondHandSection`** (`PlanResults`): warm-tinted card visually separate from the plan, with a
  condition badge, seller city, "≈ cijena okvirna / approx. price · check availability", a buyer-beware
  disclaimer (private listing, negotiable, confirm with the seller, BudgetSpace doesn't handle the deal), and a
  link straight to the live listing. Placeholder image stays (eBay seller photos aren't shown until display
  rights are confirmed — `imageVerified=false`).
- **i18n:** 9 new hr+en keys; a warm amber condition chip + section styles. Renders nothing until a feed runs,
  so today's UI is unchanged and it lights up the moment the eBay key feeds data. Frontend build clean (tsc + vite).

### Sprint 10.51 — "Rabljeno" backend: eBay Browse feed + second-hand pipeline (current)
- Owner gave the go-ahead for eBay; the developer key is pending (~1 business day) but is **not a blocker** —
  the feed ships **dormant** (the existing unconfigured-feed pattern *is* the placeholder) and the whole pipeline
  it plugs into is built + tested now. When the key lands: set 2 env vars + restart → live smoke test.
- **§3 plumbing end-to-end:** `secondHand` / `conditionLabel` / `sellerLocation` now flow
  `RetailerProductSnapshotDto → ImportProductDto → RetailerCatalogAdapter → ProductImportService → Product →
  ProductDto` (the entity already had the columns; the snapshot/import chain didn't carry them — the §3/§8.5 gap).
- **Budget-safety (a real bug, fixed):** a used eBay row (retailer `eBay`=feed-required, sourceType
  `marketplace-listing` ∈ FEED_SOURCE_TYPES) would have passed `isPlannerEligible` and **inflated the budget
  total**. Fixed at one chokepoint — `PlannerService.marketCatalog` now excludes `secondHand`, so a used item can
  never enter any plan or total. The provenance gate stayed intact (a fed listing still counts as verified).
- **Separate block:** `PlanGenerationResponse.secondHandSuggestions` — room+market-matched used listings (short
  24h marketplace freshness window), shared across all 3 plan tiers, **never summed into a total**.
- **`EbayBrowseFeed`** (implements `MarketplaceFeed`): OAuth client-credentials + Browse `item_summary/search`
  (furniture category, `conditions=USED`, per-market local filter), maps to `secondHand=true` /
  `marketplace-listing` rows with stated condition + seller city, every row through
  `MarketplaceListingFilter.shouldDrop` before return. Credentials from env only
  (`budgetspace.marketplace-feeds.ebay.client-id/client-secret`) — dormant, no network, until set. Replaces the
  eBay placeholder bean. Multilingual furniture classifier (unmappable → drop, no fabrication).
- **eBay reality:** local marketplaces only in **DE/IT/AT/FR/NL/ES** — HR/SI/FI/NO/SE/DK/SK/PT have no eBay site
  and keep their own placeholders. Localized sold markers added (verkauft/vendu/venduto/vendido/verkocht…).
- **Tests:** `EbayBrowseFeedTest` (fixture, no creds) — dormancy, market allow-list, mapping drops
  sold/unpriced/unclassifiable, and end-to-end §5 (used imported `secondHand=true`, excluded from every plan
  total, surfaced separately). **Backend 185 tests, 0 failures.**
- **Next:** the frontend "Rabljeno" section (Sprint 10.52) renders `secondHandSuggestions` with a condition
  badge, location, "cijena okvirna" + buyer-beware disclaimer + link — stays empty until the key feeds data.

### Sprint 10.50 — human copy + premium UX polish (less "ChatGPT wrapper") (current)
- Owner: make it feel like a real interior designer, not an AI explaining its algorithm; modern readable font;
  fix the broken "Prati cijenu" modal.
- **Recommendation copy rewritten (backend `PlannerService`)** — removed the AI tells ("Ovo je glavni komad",
  "ima smisla biti u prvom fokusu", "dobar omjer cijene, izgleda i korisnosti") and the algorithm-explaining
  tone. `buildReason` now speaks like a designer: one or two concrete reasons (role in the room, style match,
  budget fit). Also `roleForCategory`/`stepForCategory` (Temelj prostora / Kreni odavde), `buildSummary`,
  `buildGoodFor`, `describePlan`, the plan labels (Najpametniji izbor…) and the store-trip line. No tests assert
  the old strings (they were test data), so all green.
- **Font: Fraunces → Bricolage Grotesque** (the serif read old-fashioned; the grotesque is modern, warm,
  readable) + Inter for body. `index.html` + `--font-display`; heading weight/tracking retuned. Verified live.
- **"Prati cijenu" modal fixed** — it opened broken (consent text squished into a narrow right column, checkbox
  floating). Now left-aligned, width-capped (`max-width: 460px`), consent is a clean checkbox+flexing-text row.
- **Premium lower sections:** priority chips → clean uppercase tags with a base style (Ključno / Udobnost /
  Kasnije, was "Najvažnije / Za ugodniji prostor"); feedback buttons → conversational (soft cards, hover-lift,
  not survey pills); **tamed the extra-black weights** (900/950/850/800 → ~620–650) that made everything feel
  mechanical; accordion summaries got a rotating **chevron + hover** so they read as interactive, not placeholder.
- **Verified:** backend tests green; frontend build clean; font + landing confirmed in the live preview.
- **Rabljeno:** owner gave the go-ahead → next is an **eBay Browse API** client to fill the 10.49 marketplace
  placeholders (needs a free eBay API key as a backend env var; never committed).

### Sprint 10.49 — marketplace placeholders + pluggable MarketplaceFeed seam
- Owner: "for now I'd rather have backend placeholders for the marketplaces we can affiliate later (no open
  API), and use the ones a country actually offers." Built exactly that — **no scraping, no data invented**.
- **Per-country "Rabljeno" placeholders registered** (`ProductTaxonomy.SUPPORTED_RETAILERS` +
  `CatalogSourcePolicy` = `OFFICIAL_FEED_REQUIRED`): Njuškalo (HR, already), Bolha (SI), Willhaben (AT),
  Kleinanzeigen (DE), Subito (IT), Tori (FI), Leboncoin (FR), Marktplaats (NL), Bazoš (SK), Wallapop (ES),
  OLX (PT), Finn (NO), Blocket (SE), DBA (DK), + multi-market **eBay** (public Browse API) & Facebook
  Marketplace. Each market now has a second-hand source slot ready for a feed/affiliate.
- **Pluggable `MarketplaceFeed` seam** (`ai.budgetspace.feed`): `MarketplaceFeed` (extends `RetailerFeed`;
  contract = `sourceType=marketplace-listing`, mark second-hand, run rows through `MarketplaceListingFilter`
  before returning, never scrape); `ConfigBackedMarketplaceFeed` (unconfigured placeholder, imports nothing);
  `MarketplaceFeedProperties` (env-backed `budgetspace.marketplace-feeds.<name>.url`, blank by default — **no
  creds committed**); `MarketplaceFeedConfig` registers one placeholder bean per marketplace. The existing
  `RetailerFeedImporter` consumes them and skips each cleanly on startup. A real client (e.g. an eBay Browse
  API mapper, or a Njuškalo partner export) drops into one bean — nothing else changes.
- `MarketplaceFeedSeamTest`: all 16 marketplaces are feed-required placeholders; the default feeds are
  unconfigured + import nothing (import service never touched); the sold-guard drops PRODANO/reserved/expired.
- **Verified:** backend tests green; app runs identically (placeholders import nothing). The 3 owner worries
  are answered in code: volume → filtered feed; which items → category/location/condition; **sold → already-built
  `MarketplaceListingFilter`** (SOLD_MARKERS + 24h staleness + planner-exclusion).
- **Next when you want data:** wire the **eBay Browse API** client (free key as backend env) as the first real
  "Rabljeno" source, +`secondHand`/`condition`/`location` through the snapshot DTO, + a "Rabljeno" UI section.

### Sprint 10.48 — retail re-sweep: +13 verified retailers (maximise per-market depth)
- Owner: "go through our countries again, find more retail stores, pull the maximum." 4 scout agents probed
  fresh candidates across all 14 markets (does the product page serve the price in **static HTML** — JSON-LD /
  PrestaShop `itemprop` / Shopify / visible €/kr — or is it 403/JS-only?), then 4 URL-discovery agents gathered
  ~10-19 category-spread product URLs per usable retailer, then a hardened multi-currency sourcer fetched each.
- **+13 clean new retailers, +199 verified products** (`real-<market>-retailers-2.json`), all `MANUAL_VERIFIED_ONLY`:
  **HR Svijetnamještaja (15), SI Svetpohištva (13), IT Conforama (16), AT Interio (18), FI Masku (19),
  FR Lovely Meubles (14), PT JOM (12) + Sítio do Móvel (14), ES Miroytengo (12) + Merkamueble (12) +
  Muebles BOOM (13), NL Pronto Wonen (13), SK Drevona (13) + ASKO Nábytok (15).** Notable: **IT 1→2 retailers**
  (first non-IKEA!), **AT + FI gained their first non-IKEA/JYSK** retailer. **Conforama flipped** from
  feed-required → verified (conforama.IT serves JSON-LD; conforama.FR stays anti-bot).
- **Dropped (honest, never ship fabricated prices):** the Scandinavian non-IKEA chains (Bohus, Møbelringen, Mio,
  Em Home, Trademax, ILVA) and Westwing (DE/NL) — they're **JS-rendered SPAs**; broad sourcing produced garbage
  (Mio all `26 SEK`, ILVA all `1 DKK`, Westwing-NL `1.34 €`). Confirmed via per-row price sanity, deleted.
  NO/SE/DK stay IKEA+JYSK. (Muebles BOOM + ASKO are price-verified but image-partial → labelled placeholder.)
- **Sourcer hardened:** price = JSON-LD `offers.price` → `product:price:amount`/`itemprop=price` → visible
  (€ / kr / `:-` / `,-`), charset-aware decode, per-currency tiers, junk filter (drop < 25 unit, Westwing, U+FFFD).
- Registered across `ProductTaxonomy` + `CatalogSourcePolicy` + `PlannerService.RETAILERS` + `RealCatalogSeeder`
  + frontend `Retailer` type + `retailersByMarket`. `RetailSweepCatalogRuntimeTest` (all 13 import + eligible +
  registered); fixed `CamifFranceCatalogRuntimeTest` (Conforama no longer feed-required).
- **Verified:** backend tests green; frontend build clean; catalog **1840 rows, 0 dups** (+199).
- **Retail depth now (verified, with products):** HR 6 · ES 6 · SK 5 · DE 5 · NL 5 · SI 4 · PT 4 · FR 3 · IT 2 ·
  AT 2 · FI 2 · NO/SE/DK 2 (IKEA+JYSK). **Retail is now broadly maximised** for deterministically-fetchable stores.
- **Marketplace (Njuškalo) — answered, pending a data-source decision.** The 10.21 scaffold already solves the
  owner's 3 worries: volume → a *filtered feed*, not scraping; which items → category/location/condition filter;
  sold-guard → `MarketplaceListingFilter` (`SOLD_MARKERS` + 24h staleness + planner-exclusion). The only blocker
  is a **compliant source** — Njuškalo has no open API + we never scrape. Recommendation: **eBay Browse API** as
  the first real "Rabljeno" source (or a Njuškalo partnership). Phase-2 `MarketplaceFeed` build awaits that call.

### Sprint 10.47 — i18n coverage fix (dynamic labels) + UI/UX redesign
- **Translation audit (owner: "are translations applied on every part, for every country?").** The static
  chrome was fully translated, but **dynamic domain labels were hardcoded Croatian** in `utils/planner.ts` and
  leaked into all 12 non-HR markets: category names (on every product), room/style/level/priority labels, the
  store-count text, the store-trip recommendation and the share/export text. Fix: the 5 label maps are now
  language-aware **proxies** that translate on access (`localisedLabels()` → `translate(key, activeMarketLang)`),
  so all ~25 call sites stay unchanged. Rooms/styles/levels reuse the already-translated `form.*` option keys;
  **41 new keys** (`cat.*`, `priority.*`, `unit.stores*`, `storeTrip.*`, `share.*`, `aria.*`) added to the
  DICTIONARY and translated into all 12 languages (3 subagents, parity-checked: 409 keys each, 0 placeholder
  drift). Store-count pluralisation now uses `Intl.PluralRules`. Two `aria-label`s localised.
  - **Example-prompt bug:** the prefilled demo prompt was seeded from `t()` at mount, capturing the English
    fallback before a non-HR overlay lazy-loaded (so NO showed English). Now re-seeded when the overlay arrives
    — and the ref update is kept OUTSIDE the `setInput` updater (StrictMode double-invokes it in dev, which made
    the first naive fix bail). Verified live in NO: prompt + labels + currency all Norwegian.
- **UI/UX redesign (owner: "make it not look AI-made").** The look was system-font + pills-everywhere
  (`border-radius: 999px`) + huge diffuse shadows + radial-gradient blobs + filled eyebrow pills — generic
  template tells. Reworked the design system in `styles.css` + `index.html`: **display serif (Fraunces) for
  headings** (was system-ui — the single biggest shift) with Inter for UI; **editorial eyebrows/kickers**
  (uppercase, letter-spaced, leading rule — no pill); **considered radii** (10/14/20/26px) replacing full pills
  on buttons/cards/chips; **tight layered shadows** instead of one 90px blur; **clean paper background** (blobs
  removed); solid serif brand mark; deeper terracotta accent. Verified across HR + NO, desktop.
- **Verified:** frontend build clean; all 12 message files 409 keys / 0 missing / 0 placeholder drift; live
  preview in HR + NO. (Backend untouched.)

### Sprint 10.46 — Scandinavia: Norway + Sweden + Denmark (first non-EUR markets)
- **Why now:** the owner asked why NO/SE/DK weren't covered. The blocker was never sourcing — it was that
  the app implicitly assumed EUR. Turns out it didn't: `MarketConfig` already carried `currency`+`locale` and
  `formatCurrency` already used `Intl.NumberFormat`, so non-EUR "just worked" once a local-currency catalog
  existed. No FX needed — a plan is built from one market's catalog and compared to a budget in the **same**
  currency, so NOK/SEK/DKK are self-contained.
- **3 new markets, each IKEA + JYSK, prices in the national currency:**
  - **IKEA NO 47 / SE 52 / DK 53** — number-trick (`/no/no/`, `/se/sv/`, `/dk/da/`) from the HR article numbers
    (global across markets); per-market JSON-LD `price` + `priceCurrency` (NOK/SEK/DKK) + localized og:title name
    + verified og:image, all 152 imaged. Per-currency price tiers (≈EUR thresholds × FX).
  - **JYSK NO 32 / SE 31 / DK 50** — static product pages (URLs discovered by subagents, then deterministic
    fetch): JSON-LD `price` = current, `priceAmount` = regular, **sale shown only when `priceValidUntil`
    confirms a window** (6 real SE promos auto-detected, e.g. KOKKEDAL chair 425 was 849 SEK). All 113 imaged.
- **Backend:** `Markets.java` +NO (SE/DK already present); 6 catalog files registered in `RealCatalogSeeder`.
  IKEA/JYSK were already `DIRECT_VERIFIED`, so no policy change. `ScandinaviaCatalogRuntimeTest` (currency wiring
  + per-market import + planner-eligibility + honest-sale guard).
- **Frontend:** `markets.ts` +NO/SE/DK (NOK/SEK/DKK, flags, cities, prompt-detection) + `Lang` `no`/`sv`/`da`;
  `retailersByMarket` NO/SE/DK = IKEA+JYSK. **Natively localised** — `messages/{no,sv,da}.json` (368 keys each,
  parity-checked, translated by 3 subagents from the EN source) lazy-loaded like the rest. Fixed 2 EUR-assuming
  strings to currency-generic; `StatsStrip` now shows the active market's currency symbol (€/kr) not a hardcoded €.
- **Verified:** backend **177 tests, 0 failures**; frontend build clean (new no/sv/da chunks, main still ~77 kB
  gzip); catalog **1641 rows, 0 dups** (+265 Scandi). The app now spans **14 markets / 13 UI languages**.
- **Next clean follow-up:** PL/CZ/HU/RO (non-EUR, same recipe — IKEA number-trick + JYSK where static-priced).

### Sprint 10.45 — depth: Portugal (Moviflor) + Slovakia (Nábytok); Finland feed-required; Germany deferred
- Closing out the retailer-depth push (ES→NL→DE→PT→FI→**SK**) so every market has more than IKEA/JYSK where a
  non-IKEA retailer is deterministically fetchable. Outcomes for the last three:
  - **PT — Moviflor (moviflor.pt): 20 web-verified rows** (`real-pt-retailers.json`), all with a verified
    og:image, across 10 categories (sofa/chair/table/dining/wardrobe/dresser/desk/storage/tv-unit). **Encoding
    fix:** Moviflor serves **windows-1252**, so `fetch().text()` (UTF-8) was mangling Portuguese accents into
    U+FFFD (`Sof�`); the sourcer now decodes per the page's declared charset → `Sofá de Canto Rochester` etc.,
    0 replacement chars. Spot-checked the sofa image (1.5 MB PNG, HTTP 200).
  - **SK — Nábytok (nabytok.sk): 11 web-verified rows** (`real-sk-retailers.json`), all with a verified og:image,
    across sofa/chair/bed/dresser/storage/tv-unit (UTF-8, Slovak diacritics intact). Price from visible €.
  - **FI — Sotka (sotka.fi): not sourceable.** Product pages render the price **client-side (JS-only)** — the
    static HTML carries no price (`og:title` is a generic category label, no JSON-LD price) → registered
    `OFFICIAL_FEED_REQUIRED`. FI keeps IKEA + JYSK (no new non-IKEA/JYSK catalog).
  - **DE — deferred.** Already the deepest market (IKEA + JYSK + Otto + Segmüller + Poco). The two reachable
    candidates underwhelmed: Roller exposes no og:image / Product JSON-LD (0 verifiable images) and Möbel Boss's
    image URLs were sparse/stale (≈10/42) → adding mostly-placeholder rows is low value. Not registered.
- Registered Moviflor + Nábytok `MANUAL_VERIFIED_ONLY` and Sotka `OFFICIAL_FEED_REQUIRED` across `ProductTaxonomy`
  + `CatalogSourcePolicy` (+ `PlannerService.RETAILERS` & `RealCatalogSeeder` for the two with products) + frontend
  `Retailer` type + `retailersByMarket` (PT = IKEA + Moviflor; SK = IKEA + JYSK + Nábytok). Honest current price
  only (no fabricated discount); image only when the live og:image resolved. `PtRetailersCatalogRuntimeTest` +
  `SkRetailersCatalogRuntimeTest` (+ `CatalogSourcePolicyTest` extended for all three).
- **Verified:** backend **175 tests, 0 failures**; frontend build clean; catalog **1376 rows, 0 dups**.
- **Depth now:** HR 5 retailers; DE 5; NL 4; HR/SI/ES 3-ish; SK 3 (IKEA/JYSK/Nábytok); PT 2 (IKEA/Moviflor);
  FR 2 (IKEA/Camif). AT/IT remain IKEA-only — every other non-IKEA chain probed there is anti-bot (feed-required).
- **Next (clean follow-ups):** Scandinavia (NO/SE/DK) needs the UI to handle non-EUR currency first (`markets.ts`
  is EUR-only) — a currency feature, not a sourcing one. Big bot-blocked chains wait for official feeds.

### Sprint 10.44 — Netherlands depth: Leen Bakker + Kwantum
- Continuing the retailer-depth push (ES→**NL**→DE→PT→FI→SK). Both Dutch candidates are sourceable:
  **Leen Bakker (18) + Kwantum (12) = 30 web-verified rows** (`real-nl-retailers.json`): price from JSON-LD /
  visible €, name from og:title (entity-decoded), verified og:image where it resolved (20/30; the rest are
  honest partials with the labelled placeholder — Leen Bakker AYLO armchair spot-checked). Honest current price
  only. Registered both `MANUAL_VERIFIED_ONLY` (`ProductTaxonomy` + `CatalogSourcePolicy` + `PlannerService` +
  frontend `Retailer` type + `retailersByMarket`: NL = IKEA + JYSK + Leen Bakker + Kwantum). `NlRetailersCatalogRuntimeTest`.
- **Verified:** backend **173 tests, 0 failures**; frontend build clean; catalog **1345 rows, 0 dups**.
- **Next:** DE (Roller, Möbel Boss), then PT, FI, SK.

### Sprint 10.43 — Spain depth: non-IKEA retailers (Kenay Home + Banak Importa)
- **Start of the production-ready retailer-depth push** (owner-requested: enough stores per market that users
  have real options). A homepage fetchability probe (2026-06-18) across our markets mapped ~9 candidate non-IKEA/
  JYSK retailers (ES/NL/DE/PT/FI/SK); **AT + IT had none reachable** (all anti-bot). Working order: ES → NL → DE
  → PT → FI → SK.
- **Spain (this sprint).** Probed Kenay Home, Banak Importa, Muebles La Fábrica:
  - **Kenay Home (14) + Banak Importa (14) = 28 web-verified rows** (`real-es-retailers.json`): price from JSON-LD
    `offers.price` / visible €, name from og:title (entity-decoded), verified og:image (Kenay Orson sofa spot-
    checked). Honest current price only (no fabricated discount). Registered `MANUAL_VERIFIED_ONLY` in
    `ProductTaxonomy` + `CatalogSourcePolicy` + `PlannerService.RETAILERS` + frontend `Retailer` type +
    `retailersByMarket` (ES = IKEA + Kenay + Banak).
  - **Muebles La Fábrica → `OFFICIAL_FEED_REQUIRED`**: homepage reachable but product pages reset the connection
    (anti-bot). Registered, not sourced. Never bypassed.
  - `EsRetailersCatalogRuntimeTest`.
- **Verified:** backend **172 tests, 0 failures**; frontend build clean; catalog **1315 rows, 0 dup
  URLs/externalIds**. No fabrication, no anti-bot bypass.
- **Next:** NL (Leen Bakker, Kwantum), then DE (Roller, Möbel Boss), PT (Moviflor), FI (Sotka), SK (Nábytok).

### Sprint 10.42 — geo-IP market detection
- **Auto-pick the country from the visitor's real location**, completing the market-detection story (the other
  two layers — browser-locale guess + prompt-text detection like "in Paris" → FR — were already live and
  verified across all 11 markets).
- **Backend `geo/` package.** `GET /api/geo` returns the visitor's 2-letter country read from a CDN/proxy
  header (`CF-IPCountry`, `CloudFront-Viewer-Country`, `X-Vercel-IP-Country`, Fastly, generic) — **no IP stored,
  no third-party call, no dependency**. Returns `null` when no such header (local dev / no CDN). `GeoControllerTest`.
- **Frontend.** `fetchGeoCountry()` (best-effort, never throws); `LocaleProvider` upgrades the browser-locale
  guess to the real geo country **only on a fresh visit** (a returning visitor's saved choice is respected).
  So a French visitor with an English browser starts on France.
- **Activation:** needs a CDN/proxy in front that injects a country header (CloudFlare etc.); otherwise it
  gracefully no-ops and the app keeps its existing browser-locale + prompt detection. (No paid geo-IP service
  pulled in — same "seam now, provider later" stance as OpenAI/email; a real geo-IP lookup can be added if a
  deploy has no CDN.)
- **Verified:** backend **171 tests, 0 failures** (+GeoController ×5); frontend build clean.

### Sprint 10.41 — new market: Portugal (PT), Portuguese-localised IKEA
- **11th market** (same recipe as ES/FR — IKEA-only, no JYSK in Portugal). Added PT to `Markets.java` +
  `markets.ts` (flag 🇵🇹, Lisboa/Porto/… cities, prompt detection) + `retailersByMarket` (PT = IKEA). `Lang` += `'pt'`.
- **Full European-Portuguese localisation.** `frontend/src/messages/pt.json` (368 keys, native pt-PT informal "tu"
  — "casa de banho", "secretária", "roupeiro", "ecrã"), parity-checked (0 missing/extra/placeholder-mismatch/empty).
  **No `i18n.ts` edit** — the 10.40 `import.meta.glob` auto-discovered `pt.json` and code-split it into its own
  chunk (the main bundle did not grow).
- **IKEA PT — 73 verified rows** (`real-ikea-pt-rooms.json`): IKEA Italy set ported via the article-number trick
  to `/pt/pt/` (Portuguese name + per-market EUR price — NORDLI 469 PT — + verified og:image, ikea.com/pt
  2026-06-18). `PortugalCatalogRuntimeTest`.
- **Verified:** backend **166 tests, 0 failures**; frontend build clean (main bundle unchanged at ~77 kB gzip —
  PT is its own chunk); pt.json parity 0 issues; catalog **1287 rows, 0 dup URLs/externalIds**. No fabrication.
- **Markets now: HR, SI, AT, DE, IT, FI, FR, NL, SK, ES, PT** (11 EUR). The "fully fetchable" EUR set from the
  10.35 probe is now exhausted; further EUR depth (BE/IE partial) or non-EUR (PL/CZ/HU/RO/SE/DK, need currency UI).

### Sprint 10.40 — i18n lazy-load: per-language chunks (frontend perf)
- **Bundle no longer grows per market.** The per-language `messages/*.json` overlays were all statically imported
  and bundled (the main JS hit ~126 kB gzip with 9 languages). Replaced the static imports with
  `import.meta.glob('./messages/*.json')` so Vite **code-splits each language into its own chunk**, fetched on
  demand by `ensureLangLoaded` (called from `LocaleProvider` when the market's language changes).
- **Result:** main bundle **~126 → 77 kB gzip** (~40% smaller); each language is a separate ~7.7 kB-gzip chunk
  loaded only when that market is selected. HR (the default) downloads zero language chunks (HR/EN are inline).
- **Behaviour:** `translate()` stays synchronous and falls back to English until the active language's chunk
  arrives; `LocaleProvider` bumps a `langReady` counter on load so consumers re-render with the translations.
- **Bonus:** adding a market now needs **no `i18n.ts` edit** — the glob auto-discovers the new `{lang}.json`.
- **Verified:** frontend build clean; the build output shows the split (`index` main chunk + one chunk per
  language). i18n parity scripts (which read the JSON directly) unaffected.

### Sprint 10.39 — new market Spain (ES) + plan-generation perf optimization
- **10th market.** Added ES to `Markets.java` + `markets.ts` (flag 🇪🇸, Madrid/Barcelona/… cities, prompt
  detection) + `retailersByMarket` (ES = IKEA only — no JYSK in Spain, like FR/IT). `Lang` += `'es'`.
- **Full Spanish localisation.** `frontend/src/messages/es.json` (368 keys, native informal "tú"), parity-checked
  (0 missing/extra/placeholder-mismatch/empty). Wired into `i18n.ts`.
- **IKEA ES — 68 verified rows** (`real-ikea-es-rooms.json`): IKEA Italy set ported via the article-number trick
  to `/es/es/` (Spanish name + per-market EUR price — KIVIK 629 ES vs 749 FR vs 549 NL — + verified og:image,
  ikea.com/es 2026-06-18). `SpainCatalogRuntimeTest`.
- **Perf optimization (owner-requested — "don't let the app lag as we scale").** `PlannerService.marketCatalog()`
  is called many times while building one plan, and each call ran `productRepository.findAll()` — a full table
  scan that grew with the catalog (now 1214 rows). Added a 2-second snapshot cache (`allProducts()`): a single
  plan request now loads the catalog **once** instead of ~a dozen times. Safe because the products table is
  immutable after the startup seed (the admin import is the only runtime writer, and it's disabled in prod);
  the short TTL still reflects a dev admin-import within seconds. Guarded by `PlannerCatalogCacheTest` (findAll
  called ≤2 per `generate()`). The frontend `translate()` is already a plain key lookup, no per-call merge.
- **Verified:** backend **164 tests, 0 failures** (+Spain ×2, +cache guard); frontend build clean; es.json parity
  0 issues; catalog **1214 rows, 0 dup URLs/externalIds**. No fabrication, no 403-bypass.
- **Possible next perf step:** lazy-load per-language `messages/*.json` (the JS bundle grows ~1 file per market —
  now ~126 kB gzipped); deferred as a larger async refactor since the interactive lag (plan generation) is fixed.
- **Next EUR market when wanted:** PT (Portugal — IKEA-only, 5/5 fetchable in the 10.35 probe).

### Sprint 10.38 — new market: Slovakia (SK), Slovak-localised IKEA + JYSK
- **9th market, two retailers** (same recipe as NL). Added SK to `Markets.java` + `markets.ts` (flag 🇸🇰,
  `available:true`, Bratislava/Košice/… cities, prompt detection) + `retailersByMarket` (SK = IKEA + JYSK).
  `Lang` += `'sk'`. **Note the SK/SI + sk/sl distinction** (Slovakia/Slovak ≠ Slovenia/Slovenian).
- **Full Slovak localisation.** `frontend/src/messages/sk.json` (368 keys = same set as nl.json), native informal
  "tykanie" tone + correct diacritics, subagent-produced + **verified programmatically** (0 missing/extra/
  placeholder-mismatch/empty). Wired into `i18n.ts` `EXTRA`.
- **IKEA SK — 72 verified rows** (`real-ikea-sk-rooms.json`): IKEA Italy set ported via the article-number trick
  to `/sk/sk/` (Slovak name + per-market EUR price + verified og:image, ikea.com/sk 2026-06-18).
- **JYSK SK — 38 verified rows** (`real-jysk-sk-rooms.json`): jysk.sk, same static price structure as jysk.nl/hr
  (priceAmount = regular, JSON-LD = current). **3 rows carry a real near-term promo** (ANDRUP 429→300, HEDEHUSENE
  369→285, VEMMELEV 499→350, until 2026-06-30). ANDRUP sofa image spot-checked.
- **Verified:** backend **161 tests, 0 failures** (+`SlovakiaCatalogRuntimeTest`); frontend build clean; sk.json
  parity 0 issues; catalog **1146 rows, 0 dup URLs/externalIds**. No fabrication, no 403-bypass.
- **Next EUR markets when wanted:** ES, PT (IKEA-only, like FR/IT — both 5/5 fetchable in the 10.35 probe).

### Sprint 10.37 — new market: Netherlands (NL), Dutch-localised IKEA + JYSK
- **8th market, two retailers.** Added NL to `Markets.java` + `markets.ts` (flag 🇳🇱, `available:true`,
  Amsterdam/Rotterdam/… cities, prompt detection) + `retailersByMarket` (NL = IKEA + JYSK). `Lang` += `'nl'`.
- **Full Dutch localisation.** `frontend/src/messages/nl.json` (368 keys = same set as fr.json), native informal
  "je/jij" tone, subagent-produced + **verified programmatically** (0 missing/extra/placeholder-mismatch/empty).
  Wired into `i18n.ts` `EXTRA`.
- **IKEA NL — 78 verified rows** (`real-ikea-nl-rooms.json`): IKEA Italy set ported via the article-number trick
  to `/nl/nl/` (Dutch name + per-market EUR price + verified og:image, ikea.com/nl 2026-06-18).
- **JYSK NL — 34 verified rows** (`real-jysk-nl-rooms.json`): jysk.nl is reachable + serves static prices
  (unlike jysk.at). Same fields as jysk.hr (priceAmount = regular, JSON-LD price = current, priceValidUntil).
  **5 rows carry a real near-term promo** (price=current + originalPrice + saleEndsAt — e.g. HUNDIGE 449→325 until
  2026-06-30); the rest store the durable regular price. jysk.nl quirk: **no og:title** → name from JSON-LD
  `name`/`<title>`/slug. HUNDIGE sofa image spot-checked.
- **Verified:** backend **159 tests, 0 failures** (+`NetherlandsCatalogRuntimeTest`: both retailers import clean
  + planner builds a non-partial NL plan); frontend build clean; nl.json parity 0 issues; catalog **1036 rows,
  0 dup URLs/externalIds**. No fabrication, no 403-bypass.
- **Next:** SK (Slovakia — IKEA number-trick + JYSK, like NL), then the other EUR markets (ES/PT) when wanted.

### Sprint 10.36 — France non-IKEA breadth: Camif + FR retailer assessment
- **Probed the major French furniture chains** (raw-HTTP, browser UA, 2026-06-18). Result mirrors the 10.16
  pattern — almost all are anti-bot or JS-only:
  - **Anti-bot (DataDome/Cloudflare 403) → `OFFICIAL_FEED_REQUIRED`:** Conforama, **But** (intermittent
    DataDome), Maisons du Monde, La Redoute, Fly, Habitat, Cdiscount, Vente-unique. Registered in
    `ProductTaxonomy` + `CatalogSourcePolicy` (never bypassed).
  - **Directly verifiable → `MANUAL_VERIFIED_ONLY`:** **Camif** (camif.fr) — serves the price in static HTML
    (JSON-LD `offers.price` / visible €) + `og:image`. The one major non-IKEA FR retailer we can source.
- **Sourced 46 web-verified Camif rows** (`real-camif-fr.json`) across all core categories (sofa 9 / bed 6 /
  table 5 / storage 5 / wardrobe 4 / dresser 4 / tv-unit 3 / dining-table 3 / chair 2 / dining-chair 2 / desk 2
  / mattress 1). Each row's French name (og:title, HTML-entity-decoded) + current EUR price (JSON-LD/visible €)
  + verified `og:image` read off camif.fr (Portimo sofa + Capucine bed images spot-checked). Camif's standing
  ~−20% web price (`priceValidUntil` = +1yr schema default) is **not** a real promo → stored as the honest
  current price, no `originalPrice`/sale badge. Registered Camif in `PlannerService.RETAILERS`, frontend
  `Retailer` type + `retailersByMarket` (FR = IKEA + Camif). `CamifFranceCatalogRuntimeTest`.
- **Verified:** backend **157 tests, 0 failures**; frontend build clean; catalog **924 rows, 0 dup
  URLs/externalIds**. No fabrication, no 403-bypass.
- **Next:** NL + SK (both IKEA via number-trick + JYSK — two retailers each), per the 10.35 EUR-market probe.

### Sprint 10.35 — new market: France (FR), fully French-localised IKEA catalog
- **7th market.** Added FR to `Markets.java` (EUR) + `markets.ts` (flag 🇫🇷, `available:true`, Paris/Lyon/…
  cities, prompt market-detection) + `retailersByMarket` (IKEA-only — no JYSK in France). `Lang` widened to
  include `'fr'`.
- **Full French localisation.** `frontend/src/messages/fr.json` (368 keys = same set as it.json), native
  informal "tu" tone, produced by a subagent and **verified programmatically**: 0 missing, 0 extra, 0
  placeholder mismatches, 0 empty; brands/units/emoji/step-numbers preserved. Wired into `i18n.ts` `EXTRA`.
- **First IKEA France catalog — 72 verified rows** (`real-ikea-fr-rooms.json`). Ported the IKEA Italy set via
  the global article-number trick to `/fr/fr/`; each row's **French name** (og:title), **per-market EUR price**
  (JSON-LD — genuinely different: KIVIK 749 FR vs 629 ES vs 549 NL) and **verified og:image** read off
  ikea.com/fr on 2026-06-18. 9 SKUs not carried in FR were dropped (redirect-to-category / no price). Covers
  living-room/bedroom/home-office/kitchen/bathroom/hallway/dining; KIVIK + MARKUS images spot-checked visually.
  No fabrication, no 403-bypass. Registered in `RealCatalogSeeder`; `IkeaFranceCatalogRuntimeTest` (import clean
  + planner builds a non-partial FR plan).
- **Why France & how the next markets were chosen.** Live-probed 8 eurozone candidates (IKEA number-trick):
  FR/ES/NL/PT/SK = 5/5 fetchable; BE 3/5, IE 2/5 (partial); GR not viable (franchise on `ikea.gr`). France was
  picked first (biggest, fully fetchable). ES/NL/PT/SK remain the obvious next EUR markets (NL/SK also have JYSK).
- **Verified:** backend **156 tests, 0 failures** (+`IkeaFranceCatalogRuntimeTest`); frontend build clean;
  fr.json parity 0 issues; catalog **878 rows, 0 dup URLs/externalIds**.

### Sprint 10.34 — opt-in price-drop alerts: PriceWatch + re-check job + notifier seam + GDPR
- **`PriceWatch` entity + endpoints.** New `ai.budgetspace.pricewatch` package: entity (externalId + market +
  retailer + denormalised product snapshot + email + baseline price + threshold + consent timestamp + active +
  unsubscribe token + last-notified guard), `PriceWatchRepository`, `PriceWatchService` (validates explicit
  consent + email + a real link-bearing product; idempotent per email+product), `PriceWatchController`
  (`POST /api/price-watch`, `GET /api/price-watch/unsubscribe`).
- **Re-check job.** `PriceWatchRecheckService` (@Scheduled, `@EnableScheduling`) reuses a deterministic
  `LivePriceProbe` (raw HTTP + browser UA + JSON-LD `offers.price`, the same approach that sourced the sales;
  empty → skip, never invent a price). Alert rule (owner decision): notify only on a verified drop ≥ the watch
  threshold (default 5%), at most once per product per cooldown (7 days), and never re-notify the same/higher
  price. **Trigger OFF by default** (`budgetspace.price-watch.recheck-enabled=false`) so no surprise outbound
  fetches; the logic is fully exercisable via `runRecheck(...)`.
- **Delivery seam (owner decision: seam now, provider later).** `PriceWatchNotifier` interface +
  `LoggingPriceWatchNotifier` default wired via `@ConditionalOnMissingBean` (logs a masked alert, sends nothing).
  A real email provider (SMTP / SendGrid / Postmark) plugs in later as another bean via backend env — **no
  credentials committed**, mirroring the `RetailerFeed` / `LlmClient` seams.
- **GDPR.** Explicit opt-in (consent flag required or 400), one-click unsubscribe token in every alert, store
  only what an alert needs (email + product + threshold + consent timestamp). Email masked in logs.
- **Frontend opt-in.** A discreet "Watch price" form on each product row (email + consent checkbox with plain
  consent copy) → `POST /api/price-watch`; HR+EN + de/it/sl/fi (parity checked). GDPR copy drafted for owner review.
- **Verified:** backend **154 tests, 0 failures** (+`PriceWatchServiceTest` 7, +`PriceWatchRecheckServiceTest`
  8: drop/threshold/cooldown/re-notify-guard/probe-unavailable). Frontend build clean; i18n 368 keys/lang, 0
  issues. Live full stack: app boots clean with scheduling + the notifier seam; `POST /api/price-watch` creates
  (threshold 5, baseline = current price), de-dupes (`alreadyWatching`), and rejects no-consent / bad-email with 400.

### Sprint 10.33 — discount / sale tracking: data model + verified sales + dual saving display
- **Data model (`saleEndsAt` end to end).** Added `Product.saleEndsAt` (ISO date, the verified promo-window
  end) through the whole pipeline: `RetailerProductSnapshotDto` + `ImportProductDto` (+ back-compat ctors),
  `RetailerCatalogAdapter` (which had been **discarding `originalPrice` — passing `null`** — now wired),
  `ProductImportService` (stores it + validates `originalPrice>0` and a parseable `saleEndsAt`), `ProductDto`
  → frontend `Product` type. `price` = current, `originalPrice` = regular; "on sale" ⇔ `price < originalPrice`.
- **24 real verified JYSK HR sales populated.** Deterministic live read of each JYSK HR product page
  (`priceAmount` = regular → `originalPrice`; JSON-LD `price` = promo → `price`; `priceValidUntil` = window →
  `saleEndsAt`); only rows where promo < regular. Examples: HUGO lamp 49.99→25 (−50%, the famous 10.23 case),
  EGEBY 69.99→35, ANDRUP sofa 399→315, MARKSKEL table 429→215 — all valid until 2026-06-21 (one row with a
  schema.org +1yr default window kept its discount but no end date). `priceTier` recomputed from the new price.
  **No fabrication, no 403-bypass.**
- **Dual saving display (PlanResults product row).** Verified sale → struck-through regular price next to the
  current price, a discreet "Na popustu / On sale" badge, `−X% · ušteda Y €`, and a "vrijedi do {date}" window.
  New i18n keys (`results.saleSaving`/`results.saleEnds`/`results.regularPrice`) in HR+EN + de/it/sl/fi (parity
  checked). Honest guard: once `saleEndsAt` passes, the discount is hidden (no stale "−40%").
- **Verified:** backend **139 tests, 0 failures** (+`SaleCatalogRuntimeTest`: whole catalog imports clean, every
  on-sale row has `originalPrice>price` + parseable `saleEndsAt` + planner-eligible + DTO round-trip; EGEBY
  anchor). Frontend build clean; i18n parity 0 issues. Live full stack: `/api/products` and a generated
  living-room plan both carry `originalPrice`+`saleEndsAt`; the planner naturally **selects** the cheaper
  on-sale items. Catalog **806 rows, 0 parse errors, 0 dup URLs/ids**.
- **Next:** opt-in `PriceWatch` + scheduled re-check job (reusing the deterministic extractor) + a
  `PriceWatchNotifier` seam (log-only default, email provider later) + GDPR opt-in/unsubscribe (Sprint 10.34).

### Sprint 10.32 — per-language localisation (DE / IT / SL / FI)
- The app no longer shows English to non-HR markets — each renders in its own language: **HR Croatian, SI
  Slovenian, AT+DE German, IT Italian, FI Finnish** (English remains the fallback for any missing key).
- Refactor: `Lang` widened to `hr|en|de|it|sl|fi`; each market's `lang` set accordingly in `markets.ts`. The
  inline `i18n.ts` DICTIONARY stays the HR+EN source of truth; the four new languages live in
  `frontend/src/messages/{de,it,sl,fi}.json` (one key→string map each) and are merged in `translate()` with an
  EN→HR→key fallback chain. `{param}` interpolation unchanged.
- Translations: extracted the 354 EN source strings, then **4 parallel subagents** (one per language) produced
  native, informal "du/tu"-tone JSON. Verified every file programmatically: **354/354 keys, 0 missing, 0 extra,
  0 placeholder mismatches, 0 left identical to English**. Brand names, `m²/€/PDF/AI`, emoji and step numbers
  preserved.
- Verified at runtime (vite + preview): switching country flips the whole UI + `<html lang>` — DE "Beschreib,
  was du willst.", IT "Descrivi cosa vuoi.", SL "Opiši, kaj želiš.", FI "Kerro mitä haluat.", HR unchanged.
  Frontend build clean (tsc + vite). Next feature (owner-requested): discount / sale tracking + price alerts.

### Sprint 10.31 — EU depth: deepen IT + FI bedroom + home-office
- IT/FI were thin (IT IKEA-only; bedroom/home-office shallow). Ported **52 verified IKEA rows** (IT 28, FI 24)
  via the global article-number trick to `/it/it/` + `/fi/fi/`: beds, mattresses, nightstands, wardrobes,
  dressers (bedroom) + desks, chairs, storage (home-office). Each row carries the verified tags from its
  existing catalog twin and a freshly fetched **per-market EUR price** (genuinely differs — e.g. 379 IT / 349
  FI; 49.95 IT / 59 FI) + **verified `og:image`** (slug-matched, resolves); spot-checked the NORDLI bed photo.
  `dataQuality=complete`. Coverage now **IT 84 rows (bedroom 26 / office 16) · FI 93 (bedroom 22 / office 16)**.
- `real-eu-bedroom-office-10-31.json` + `EuBedroomOfficeCatalogRuntimeTest`; backend **137 tests, 0 failures**;
  catalog **806 rows, 0 duplicate productUrls/externalIds**. No fabrication, no 403-bypass.
- Remaining EU breadth: SI/AT (IKEA/JYSK) are mid-depth; could add more, but every market now covers the core
  rooms. Per-language localisation is the next UI step (10.32).

### Sprint 10.30 — QoL pass: form usability + market-aware location & stores
- **Left sidebar scrolls on its own** (`.planner-panel` max-height + `overflow-y` inside the sticky column),
  so the user reaches every field without scrolling the whole page first; natural flow on mobile.
- **Room size field**: label → "Kvadratura"/"Floor area" with an **`m²` unit suffix inside the input**
  (mirrors the budget `€` field); humanised the count word "stvar/stvari" → "komad/komada" (EN keeps item/s).
- **Country picker with flags + per-country city combobox** replaces the free location text field: a
  flag-prefixed `<select>` (🇭🇷🇸🇮🇦🇹🇩🇪🇮🇹🇫🇮) drives the market, and a `datalist`-backed city input
  suggests that country's cities while still allowing free entry (`CITIES_BY_MARKET` in markets.ts). Header
  market `<select>` also shows flags now (both stay in sync).
- **Market-aware store list**: the "Where do you want to shop" pills now show only the stores that actually
  have products in the selected country (`retailersByMarket`: HR=IKEA/JYSK/Emmezeta/Harvey Norman/Namjestaj;
  DE=+Otto/Segmüller/Poco; IT=IKEA-only; …). Switching country resets the selection to that market's stores
  (so e.g. IT never keeps an HR-only store → empty plan). **This unlocks the previously-unreachable Harvey
  Norman / Namjestaj.hr / Otto / Segmüller / Poco products** — the form only ever sent the fixed 6 retailers,
  so the backend (which already knows all 11) never received them. Expanded the `Retailer` TS type to match.
- Verified at runtime (vite + preview eval): country flags, IT→[IKEA] store reset, Italian city list, header
  sync all correct. Frontend build clean; 0 missing i18n keys.

### Sprint 10.29 — EU depth: fill the IT + FI dining-room gap
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
