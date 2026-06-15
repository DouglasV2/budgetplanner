# Next steps

## Sprint 2: make it feel real

1. Add saved plans table.
2. Add shareable public plan URL.
3. Add product detail modal.
4. Add analytics events:
   - prompt submitted
   - plan generated
   - product clicked
   - product replaced
   - plan copied
5. Add affiliate-ready outbound links with `utm_source=budgetspace`.

## Sprint 3: LLM layer

Do not let the LLM invent products. Let backend retrieve candidates first, then pass only those candidates to the LLM.

Flow:

```text
Prompt
  -> parse intent
  -> query products
  -> deterministic candidate shortlist
  -> LLM ranks/explains plan
  -> backend validates total price and product IDs
```

## Sprint 4: scraping

Start with one retailer and one category.

Suggested first vertical:

```text
IKEA + JYSK living room products
```

Store raw scrape payloads separately so bugs do not corrupt normalized product data.

## Sprint 10.10 (done): AI intent extraction + monetization guardrails

Shipped:

- LLM provider abstraction (`LlmProvider`, `LlmClient`, `OpenAiLlmClient`, `AnthropicLlmClient`,
  `LlmClientFactory`) — provider-agnostic, off by default, keys backend-only.
- `PromptIntelligenceService` → `PlannerIntentAnalysisDto` (strict JSON), mapped to `PlannerInputDto`;
  the deterministic planner still picks real products (LLM never invents products/prices/URLs).
- Rule-based fallback on every failure path (disabled / no key / malformed / limit hit).
- `AiUsageTracker` — in-memory usage + cost/rate guardrails (monthly USD, per-day, per-session).
- Affiliate/sponsored groundwork on the product model (`originalProductUrl`, `affiliateUrl`,
  `sponsored`, `sponsorLabel`) — no aggressive ad UI.
- Value-first pricing UI (Free / Pro / Pro+), AI-insight + low-confidence display.

## Sprint 10.11 (done): room catalog coverage

Added a verified IKEA + JYSK HR snapshot (`real-ikea-jysk-hr-rooms-expansion.json`) so **every room
except home-gym** now produces a complete plan from real products with real URLs:

- bedroom (bed, mattress, nightstand, wardrobe, dresser), home-office (desk, chair, storage),
  bathroom (storage cabinets — IKEA, since JYSK HR has no online bathroom cabinets).
- IKEA prices are web-verified (the earlier "no price" cases were discontinued products).
- home-gym stays on sample data (IKEA/JYSK don't sell gym equipment).

## Sprint 10.12 (done): catalog depth + calmer form

- Added `real-ikea-jysk-hr-depth.json` (more lighting/rugs/decor and extra per-category options) so
  matching has more real candidates across colour/material/budget tiers.
- Collapsed the long "Podesi ako želiš" controls into a `<details class="advanced-settings">` block;
  the prompt box stays the primary path and the advanced fields are opt-in.

## Sprint 10.13 (done): third HR retailer + reviews (#2) + EU markets/i18n (#3)

**Retailer coverage (HR):**

- Added **Emmezeta** as a third verified HR retailer (`real-emmezeta-hr.json`, 4 web-verified
  products). **Decathlon, Pevex and Lesnina (xxxlesnina.hr) could not be added**: their pages return
  HTTP 403 to our fetch, so prices/URLs are unverifiable and we do not fabricate them. They need an
  official product feed / partner access before they can ship. (home-gym still depends on Decathlon,
  so it stays on sample data.) See `docs/real-catalog-source.md`.

**#2 Reviews / availability:**

- `Product` gained `reviewCount` + `reviewsUrl`, threaded through the import/snapshot path and exposed
  on `ProductDto` (with `reviewsUrl` falling back to the product page).
- The plan UI shows the aggregate `★ rating (count)` **only when a verified count exists** and a
  "Recenzije u trgovini" link so the shopper reads the real reviews and checks availability in store.
  We never invent ratings.

**#3 EU markets + currency + i18n:**

- Backend `Markets` helper (market → currency/locale; `HR` default; `null` market = global) + a
  `market` field on `Product` and `PlannerInputDto`. `PlannerService` filters the catalog by market
  while still matching global products, so existing data/tests are unaffected.
- Frontend: `markets` config, lightweight i18n (HR default + EN), `LocaleProvider`, header country
  selector, market-aware `formatCurrency`, market wired into the generate request, and a
  "catalog still being populated" note for non-HR markets.

### What's next

1. **Verified review data**: the reviews UI is fully wired but invisible until products carry a real
   `reviewCount`/`reviewsUrl`. Populate from an official feed/partner data — do not scrape or guess.
2. **Per-market catalogs**: real catalog products are currently `market="HR"`; other EU markets get
   only global/sample products. The selector intentionally offers **EUR markets only** (prices are in
   EUR). Add verified per-market catalogs (and currency-correct data) before enabling non-EUR markets.
3. **Fuller i18n**: only high-visibility strings are translated (header, generate button, pricing,
   market note, planner heading). Extend the dictionary to the rest of the planner copy.
4. **Unblock retailers**: get an official feed / partner access for Decathlon, Pevex, Lesnina (and
   thus home-gym), instead of HTML fetching that returns 403.
5. **Expand depth**: more products per category/colour/material/budget tier; richer bedroom/home-office
   lighting/rug/decor (currently thin → may fall back to samples).
6. **Wire AI in a staging env**: set `BUDGETSPACE_AI_ENABLED=true`, `BUDGETSPACE_LLM_PROVIDER=openai`
   and a real `OPENAI_API_KEY`; verify intent extraction quality on messy Croatian prompts.
7. **Persist usage**: move `AiUsageTracker` to a DB table (`ai_usage_event`) when multi-instance.
8. **Stripe**: only after pricing is validated; keep the value-first, context-aware upgrade approach.
9. **Affiliate population**: fill `affiliateUrl`/`originalProductUrl` from a partner program; keep
   sponsored clearly labelled and never replacing the best organic pick.
10. **Structured outputs**: consider OpenAI `json_schema` strict mode / official SDKs once stable.
