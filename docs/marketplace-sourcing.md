# Second-hand marketplace section — design (Sprint 10.17, design-first)

A design for a **"Rabljeno / second-hand"** source: surface used furniture from consumer marketplaces
(**Njuškalo**, **Facebook Marketplace**, later others) so a plan can offer a cheaper, greener
alternative next to the new-retail picks. This document is the **plan**; no marketplace code ships in
this sprint. It is binding the same way [`sourcing-policy.md`](sourcing-policy.md) is: the integrity and
anti-bot rules below are not negotiable when the feature is built.

> TL;DR: marketplaces are **user-listed, ephemeral, single-unit, ToS-protected** inventory. We treat
> them exactly like a feed-required retailer — **integrate an official API / partner feed, never scrape**
> — add a `marketplace-listing` provenance and a `second-hand` product flag, show them in a **separate,
> clearly-labelled section**, and **aggressively drop sold/unavailable listings** so we never link a
> user to a dead or `PRODANO` ad.

## 1. Why a marketplace is not a retailer (the model is different)

| Dimension | Retail catalog (IKEA/JYSK/…) | Marketplace (Njuškalo/FB) |
|---|---|---|
| Inventory | Stable SKUs, restocked | One-off listings, **disappear when sold** |
| Quantity | Effectively unlimited | **Exactly 1** (or a few) |
| Price | Authoritative, fixed | Asking price, **often negotiable** |
| Quality | New, consistent | **Used; condition varies**; photos are the seller's |
| Identity | The retailer | A **private seller or small business** |
| Trust | Brand-backed | **Buyer-beware**; scam risk; must meet/inspect |
| Reviews | Aggregate star rating | None (no `reviewRating`/`reviewCount` — leave null) |
| Access | Public product pages (IKEA/JYSK) | **ToS-restricted; anti-bot; no open product API** |

Consequence: a marketplace listing is **not** a `Product` we can treat like a catalog row. It needs its
own provenance, freshness, trust handling and UI, and it must never silently inflate the new-furniture
plan total.

## 2. Sourcing rule (same architecture as a 403 retailer — never scrape)

The rule from [`sourcing-policy.md` §1](sourcing-policy.md) applies in full: **we do not defeat anti-bot
protection or violate Terms of Service.** No proxy rotation, no stealth/headless browser, no spoofed
fingerprints, no scraped cookies/sessions, no private/internal endpoints.

- **Facebook Marketplace** — Meta's ToS **prohibits** automated collection, there is **no public listings
  API**, and the site is bot-protected. → `OFFICIAL_FEED_REQUIRED`. Only viable via an official
  Meta/Commerce API or partner integration the user is authorised for. Until then: **registered, no data.**
- **Njuškalo** — large Croatian classifieds. Treat as `OFFICIAL_FEED_REQUIRED`: integrate only via an
  **official API / partner / data-export agreement** if one is available; **do not scrape** the public
  site even if pages happen to render. Probe politely (robots.txt + ToS) before any integration; if
  blocked or disallowed, it stays feed-required with no data.
- Same seam as retail feeds: implement the marketplace source behind the
  [`ai.budgetspace.feed`](../backend/src/main/java/ai/budgetspace/feed/) contract (see §8). A source that
  is not configured imports **nothing** and never crashes the app.

If a compliant feed is never available, the section simply stays empty — **honesty over coverage**, as
everywhere else.

## 3. Provenance & data model

### New `sourceType`: `marketplace-listing`
Add to `ProductTaxonomy.SOURCE_TYPES` (and document in `sourcing-policy.md §3`). It marks a row that
came from a consumer marketplace via a compliant feed — distinct from `official-feed` (a retailer's own
feed) because the trust/quality/freshness rules differ.

### `second-hand` marker + listing fields
A used listing is modelled as a `Product` with `secondHand=true` plus marketplace-specific fields
(new columns, all nullable so the existing catalog is untouched):

| Field | Example | Notes |
|---|---|---|
| `secondHand` | `true` | Drives the separate UI section + "rabljeno" label. Default `false`. |
| `condition` | `like-new` \| `used-good` \| `used-fair` \| `for-parts` | Required for a listing; shown to the user. Never guessed — only if the listing states it. |
| `sellerType` | `private` \| `business` | From the listing. |
| `location` | `"Zagreb"` | City/region only; helps the user judge pickup distance. No precise address. |
| `postedAt` / `listingId` | `2026-06-14` / feed id | For freshness + dedup. |
| `availabilityStatus` | `in-stock` → `unavailable` | Reused; a listing is single-unit (see §4). |
| `price` | asking price | Mark as "okvirno / dogovor" in UI; marketplaces negotiate. |

`reviewRating`/`reviewCount` stay **null** (no reviews on a private listing). The planner's internal
`rating` stays `0` like other imported rows.

## 4. The availability guard — never surface a sold or dead listing (explicit requirement)

This is the part that protects user trust. A second-hand listing is **gone the moment it sells**, so
the pipeline must be defensive on the way in and on every refresh.

**Ingest-time filter (drop the row, do not import):**
- the listing is not reachable / 404 / removed, **or**
- the title or status contains a sold/closed marker (case-insensitive, accent-insensitive):
  `PRODANO`, `prodano`, `SOLD`, `REZERVIRANO`/`rezervirano` (reserved), `nije dostupno`, `neaktivan`,
  `završeno`/`zavrseno` (ended), `povučeno`/`povuceno` (withdrawn), `gotovo`. Maintain this list in one
  constant (`MarketplaceListingFilter.SOLD_MARKERS`) so it is testable and extendable.
- no concrete price, or no usable photo, or missing condition → drop (we never fabricate any of these).

**Refresh-time expiry (a listing that was fine yesterday may be sold today):**
- Marketplace freshness window is **short** — hours/1 day, **not** the 14-day `STALE_AFTER_DAYS` used for
  retail. Add a separate `MARKETPLACE_STALE_AFTER_HOURS` (proposed 24h). Past it, the row is stale →
  excluded from plans and shown only with a "provjeri je li još dostupno" note, or removed.
- On each feed refresh, a listing **absent from the new snapshot** is marked `unavailable` (then pruned),
  not left lingering. `ProductTaxonomy.canEnterPlanner` already rejects `unavailable`, so a sold item
  cannot enter a plan once flagged.
- The "Otvori oglas" link is the only CTA; we always tell the user availability is **not guaranteed** and
  to confirm with the seller before traveling.

Net effect: **a `PRODANO`/reserved/expired listing is never fetched into the catalog, never scored by the
planner, and never shown with a live link.**

## 5. Planner & UX treatment

- **Separate section, never mixed silently.** New-retail plans stay as they are. Used items appear in a
  distinct **"Rabljeno (second-hand)"** block, e.g. "Slično rabljeno u blizini — provjeri dostupnost".
  A used item must **not** be counted into the value/budget/stretch totals by default (different trust
  model); it is an optional, clearly-marked alternative.
- **Honest labelling:** every card shows a "rabljeno" badge, the `condition`, `location`, "cijena
  okvirna / po dogovoru", and a disclaimer ("Privatni oglas — dostupnost i stanje provjeri sa
  prodavačem; BudgetSpace ne jamči transakciju."). Photos are the seller's, shown only if the feed
  provides them with rights to display; otherwise the labelled placeholder, as today.
- **Trust & safety copy:** brief buyer-beware guidance (meet in person, inspect, avoid prepayment) since
  marketplace fraud is real. No messaging/payment is handled in-app — we only link out.
- Matching reuses the existing room/style/category/colour scoring, so a "used grey 3-seater sofa in
  Zagreb" can be matched to a living-room sofa request.

## 6. Monetization

- **No affiliate, no sponsored on used listings.** `affiliateUrl` stays null; `sponsored=false`. The link
  is the plain marketplace listing URL. This keeps the feature value-first and avoids any incentive to
  surface a worse used item. (Consistent with [`sourcing-policy.md §7`](sourcing-policy.md).)

## 7. Security & integrity (never violate)

- Never scrape, never bypass anti-bot, never breach ToS (see §2).
- Never commit API tokens/cookies/credentials for any marketplace; read them from env at deploy time,
  same as the retailer feed seam.
- Never fabricate a listing, price, condition, photo or location — unverifiable → drop the row.
- Don't store personal data of sellers beyond what's needed to show a city + open the public listing.

## 8. Implementation seam (for the future build sprint — not now)

When a compliant feed exists:
1. `CatalogSourcePolicy`: register `"Njuškalo"`, `"Facebook Marketplace"` in
   `ProductTaxonomy.SUPPORTED_RETAILERS` with status `OFFICIAL_FEED_REQUIRED` (a marketplace is
   feed-required by definition). Optionally add a `MARKETPLACE` source-kind so the policy can tell a
   marketplace apart from a blocked retailer.
2. `ProductTaxonomy.SOURCE_TYPES`: add `marketplace-listing`.
3. New `MarketplaceListingFilter` (the §4 sold/expiry rules) + unit tests on the `SOLD_MARKERS` list.
4. A `MarketplaceFeed` implementing the `ai.budgetspace.feed.RetailerFeed` contract (or a sibling
   interface) that maps the compliant feed into `RetailerProductSnapshotDto` rows with
   `sourceType=marketplace-listing`, `secondHand=true`, `condition`, `location`, short freshness.
5. `Product` + DTO: add the nullable second-hand columns (§3). `ddl-auto=create` rebuilds the schema.
6. Frontend: a `SecondHandSection` component + "rabljeno" badge + disclaimer; never merged into the main
   plan totals.
7. Tests mirror the catalog runtime tests: a fixture feed imports cleanly, a `PRODANO`/expired fixture is
   **rejected**, and a used row never enters the new-retail plan total.

## 9. Phased rollout

1. **Design (Sprint 10.17):** ✅ this document + backlog entry.
2. **Phase 1 (Sprint 10.21):** ✅ done — `marketplace-listing` provenance (`ProductTaxonomy.SOURCE_TYPES`
   + `CatalogSourcePolicy.SOURCE_MARKETPLACE_LISTING`, added to `FEED_SOURCE_TYPES`); `Njuškalo` +
   `Facebook Marketplace` registered as `OFFICIAL_FEED_REQUIRED`; `MarketplaceListingFilter` (the §4
   sold/expired guard, with `SOLD_MARKERS` + `MARKETPLACE_STALE_AFTER_HOURS=24` + `shouldDrop`);
   `Product.secondHand` / `conditionLabel` / `sellerLocation` columns; `MarketplaceListingFilterTest` +
   `MarketplaceSourcingPolicyTest`. No feed/data/UI yet — imports nothing (scaffold-first, like 10.14).
3. **Phase 2:** integrate the first **compliant** source (whichever of Njuškalo's partner API / a sanctioned
   export becomes available) — a `MarketplaceFeed` mapping rows to `sourceType=marketplace-listing`, each run
   through `MarketplaceListingFilter` before import. FB Marketplace only if an official Commerce API path is
   authorised. Never scrape.
4. **Phase 3:** UI section ("Rabljeno") + trust/safety copy + freshness monitoring; used items kept out of
   the new-retail plan total.

## 10. Open questions

- Does Njuškalo offer an official API / partner / affiliate or data-export program? (Decision gate for
  Phase 2 — without it, no integration.)
- Display rights for seller photos under the chosen feed's terms?
- Should used items ever be allowed to *replace* a new pick when far cheaper, or always remain a separate
  suggestion? (Default: separate.)
