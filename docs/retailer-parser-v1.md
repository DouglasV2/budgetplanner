# Retailer-specific parser v1 — IKEA

Sprint 9.3. Prvi konkretniji parser za **jednu** trgovinu (IKEA), koji radi nad eksplicitno
danim product URL-ovima i offline HTML fixtureima. Nije crawler i ne pokriva sve layout
varijante — cilj je stabilan v1 za male URL liste.

## Što IKEA parser v1 radi

`IkeaProductParser` je namjerno malen i nadograđuje generički parser:

- **prepoznaje IKEA stranicu** — po `retailer == "IKEA"` ili domeni koja sadrži `ikea.`,
- **stabilan `externalId`** — iz IKEA broja artikla u URL-u (8 znamenki, npr. `30299118`
  ili `302.991.18`) → `ikea-30299118`; ako ga nema, koristi se izvedeni id iz URL sluga,
- **čišćenje naziva** — miče sufiks trgovine (`… - IKEA`, `… | IKEA`, `… - IKEA Hrvatska`).

Sve ostalo (cijena, slika, dostupnost) ide kroz generički redoslijed:

1. **JSON-LD** `Product` schema (cijena, slika, dostupnost, naziv),
2. **retailer-specific** korak (čišćenje naziva, article id),
3. **OpenGraph/meta** fallback (`og:title`, `og:image`, `product:price:amount`, `product:availability`),
4. **`<title>`** kao zadnji fallback za naziv,
5. **request `defaults`** za `category` / `roomTags` / `styleTags`.

## Mapiranje dostupnosti

`schema.org` / meta vrijednost → naša vrijednost:

| Izvor | Rezultat |
| --- | --- |
| InStock / instock | `in-stock` |
| LimitedAvailability / limited | `limited` |
| OutOfStock / SoldOut / Discontinued | `unavailable` |
| PreOrder / BackOrder / InStoreOnly | `check-store` |
| nepoznato / nije pronađeno | `check-store` (uz warning) |

## Kvaliteta podataka (interno, ne prikazuje se korisniku)

- **complete** — strukturirani (JSON-LD) podaci + naziv, cijena, `productUrl`, slika,
  `category`/`roomTags`/`styleTags` (iz defaults) i sigurna dostupnost.
- **partial** — ima naziv i cijenu, ali npr. fali slika, dostupnost nije sigurna ili je
  korišten OpenGraph fallback.
- **needs-review** — fali cijena ili naziv. Ne uvozi se kao usable; ide u `reviewItems`.

Nema „score” u korisničkom UI-u. `needs-review` proizvodi ne ulaze u planner.

## Offline fixtures (testovi)

U `backend/src/test/resources/collector/`:

- `ikea-product-jsonld-complete.html` — potpun JSON-LD → `complete`, čist naziv, article id,
- `ikea-product-jsonld-missing-price.html` — JSON-LD bez cijene → `needs-review`,
- `ikea-product-og-fallback.html` — bez JSON-LD, OG meta → `partial` + warning,
- `ikea-product-unavailable.html` — `availability: OutOfStock` → `unavailable`.

Testovi (`RetailerProductParserTest`) ne zovu živi internet — koriste ove fixture.

## Kako dodati drugu trgovinu kasnije

Napravi mali parser kao `IkeaProductParser` (prepoznavanje domene + 2–3 stabilna pravila),
pokrij ga fixtureima i uključi u `RetailerProductParser`. Ne hardkodiraj stotine selectora —
oslanjaj se na JSON-LD/OpenGraph, a retailer-specific samo kao fallback.
