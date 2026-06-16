# Catalog sourcing policy (Sprint 10.14)

This is the **architectural rule** for how products get into the catalog. It is enforced in code by
[`CatalogSourcePolicy`](../backend/src/main/java/ai/budgetspace/product/CatalogSourcePolicy.java) and the
feed layer under [`ai.budgetspace.feed`](../backend/src/main/java/ai/budgetspace/feed/). Treat it as
binding for every future sprint.

## 1. A 403 is not solved by a bypass

When a retailer returns **HTTP 403** (or otherwise blocks automated access) we **change the sourcing
strategy** — we do **not** defeat the protection. The following are explicitly out of scope and must
never be added:

- ❌ proxy rotation, residential proxies, IP cycling
- ❌ headless/stealth browsers, JS rendering to get around bot checks
- ❌ spoofed/rotated browser fingerprints or forged `User-Agent` strings to impersonate a browser
- ❌ reusing cookies / sessions / auth tokens scraped from a logged-in browser
- ❌ private/internal `search` / `filter` / `module` / `stock` / Algolia / GraphQL endpoints
- ❌ any brittle workaround whose only purpose is to get past a block

Instead, a blocked retailer becomes **`OFFICIAL_FEED_REQUIRED`** and is populated only by an
official/partner feed or by hand-verified products. The collector
([`RetailerCollectorService`](../backend/src/main/java/ai/budgetspace/collector/RetailerCollectorService.java))
**refuses such retailers up front** and logs the reason; the HTTP fetcher keeps its plain, honest
`User-Agent` and simply reports a 403 as a clean failure (no retry, no bypass).

## 2. Per-retailer sourcing status

| Retailer | Status | What it means |
|---|---|---|
| IKEA | `DIRECT_VERIFIED` | Public product pages are reachable and hand-verified. Controlled import allowed. |
| JYSK | `DIRECT_VERIFIED` | Same as IKEA. |
| Emmezeta | `MANUAL_VERIFIED_ONLY` | Hand-verified, link-out only. No automated fetch (no prices/ratings to read). |
| Decathlon | `OFFICIAL_FEED_REQUIRED` | `decathlon.hr` returns **HTTP 403** on the homepage (WAF/anti-bot). Needs an official feed. |
| Pevex | `OFFICIAL_FEED_REQUIRED` | `pevex.hr` returns **HTTP 403**. Needs an official feed. |
| Lesnina | `OFFICIAL_FEED_REQUIRED` | `xxxlesnina.hr` returns **HTTP 403**. Needs an official feed. |

Re-confirmed 2026-06-16. `home-gym` depends on Decathlon, so it stays on sample/manual data until a
Decathlon feed exists — it must **not** be sourced by scraping.

## 3. Import-source provenance

Every imported product records where it came from in `Product.sourceType`. Canonical values:

| Value | Meaning |
|---|---|
| `manual-verified` | A human verified the name/price/URL by hand. |
| `public-product-page` | Verified from a publicly reachable product page (IKEA/JYSK). |
| `official-feed` | Delivered by the retailer's official/partner feed. |
| `affiliate-feed` | Delivered by an affiliate-network feed. |

Pre-10.14 values stay valid: `manual`, `retailer-snapshot` (≈ `public-product-page`) and
`future-scraper` (collector). All allowed values are validated in `ProductImportService`.

## 4. The production / verified-catalog gate

`CatalogSourcePolicy.isProductionVerified(Product)` is the single definition of "verified". A product
is verified **only** when all hold:

1. it can enter the planner (`ProductTaxonomy.canEnterPlanner` — in stock, priced, has room + style,
   and is **not** `needs-review`),
2. it is **not stale** (price/availability checked within `STALE_AFTER_DAYS`),
3. it carries a real `sourceReference` (legacy `data.sql` sample rows have none → excluded), and
4. if its retailer is `OFFICIAL_FEED_REQUIRED`, the product actually came from a feed
   (`official-feed`/`affiliate-feed`) — never from scraping a blocked site.

So **`NEEDS_REVIEW`, `STALE` and sample products are never verified.** When a price, image, URL or
review cannot be trusted, the row must be `needs-review` (or stale), not presented as verified.

> Note: the planner today still serves sample data for rooms that have no real catalog yet (notably
> `home-gym`) — that is the intentional fallback, not a verified plan. Flipping the planner to
> verified-only globally requires first sourcing those rooms; see the gate above for the predicate to
> switch on when that work lands.

## 5. Official / partner feed integration

The feed seam lives in [`ai.budgetspace.feed`](../backend/src/main/java/ai/budgetspace/feed/):

- `RetailerFeed` — the contract a real feed implements (`isConfigured()`, `fetchSnapshot()`).
- `ConfigBackedRetailerFeed` — the default, **unconfigured** slot for each feed-required retailer.
- `RetailerFeedProperties` — reads feed URLs from the environment; **blank by default**.
- `RetailerFeedImporter` — on startup imports configured feeds through the validated snapshot pipeline
  and **cleanly skips** unconfigured ones (logging the reason). A feed that throws is caught so the
  app never crashes.

### Configuring a feed (deploy-time only)

Set the URL via an environment variable — **never commit it**:

```bash
BUDGETSPACE_FEED_DECATHLON_URL=...   # or _PEVEX_ / _LESNINA_
```

Any API key/token the feed needs is read by its own client at that point, **not** stored in the repo,
`application.yml`, or `.env.example`. To go live, drop in a `RetailerFeed` implementation that fetches
the feed and maps it into verified `RetailerProductSnapshotDto` rows (with `sourceType=official-feed`).

## 6. Security rules (never violate)

- never commit `.env`, API keys, affiliate tokens, or cookies/sessions
- never add browser impersonation, proxy or stealth scraping
- never fabricate products, prices, reviews, images or URLs — if it is not verifiable, mark it
  `needs-review` and leave `imageUrl` null/placeholder

## 7. Affiliate / monetization guardrails

`affiliateUrl` may exist but must **never replace** `originalProductUrl` — the "Otvori u trgovini"
link always points to the real retailer page. `sponsored`/`sponsorLabel` must be transparent and
discreet and must never displace the best organic pick. The core AI room plan stays free.
