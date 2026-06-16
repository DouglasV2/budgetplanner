# BudgetSpace AI — Tasks

Living backlog + done log. Pair with `MEMORY.md` and `ARCHITECTURE.md`.

## Recently done

### Sprint 10.15 — production catalog depth (current)
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
2. **More catalog depth where thin.** Per market: more sofas/tv-units/rugs/decor; kitchen + hallway +
   bathroom coverage for SI/AT/DE; raise per-market counts toward HR parity. Same rule: verify each.
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

## Manual test prompts (rule-based, no LLM spend)
Pick the country (top-right) to match the market, then paste the wish. Markets with data: HR, SI, AT, DE.

- **HR · dnevni boravak**: „Imam 1500 € za dnevni boravak, moderno, najviše IKEA, već imam TV i tepih."
- **HR · spavaća**: „Spavaća soba do 1200 €, minimalistički, trebam krevet, madrac, ormar i noćne ormariće."
- **HR · blagovaonica**: „Blagovaonica do 800 €, kombiniraj IKEA i JYSK, trebam stol i 4 stolice."
- **DE · Wohnzimmer (all IKEA)**: „Imam 1800 € za dnevni boravak i želim sve iz IKEA-e, svijetli stil, već imam TV."
- **DE · spavaća (complete)**: „Spavaća soba 1500 €, kompletno, minimalistički — krevet, madrac, ormar, komoda."
- **AT · radni kutak**: „Radni kutak do 600 €, moderno, trebam radni stol, uredsku stolicu i policu."
- **SI · blagovaonica**: „Jedilnica do 1000 €, moderno, miza in stoli." (planner razumije i HR/EN pojmove)
- **HR · home-gym (još na sample podacima)**: očekuj djelomičan plan / placeholder dok ne dođe Decathlon feed.

Expected: 3 plans (value/budget/stretch), real product names + EUR prices + "Otvori u trgovini" links,
market badge for non-HR, no fake ratings, TV/tepih excluded when "već imam …".
