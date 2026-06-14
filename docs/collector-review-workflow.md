# Collector review workflow (dev-only)

Sprint 9.2. Kako čitati rezultat collectora i popraviti ono što nije ušlo. Ovo je dev/docs
workflow — nema frontend UI-a, nema schedulera, nema crawlera.

## Brojevi u summaryju

| Polje | Značenje |
| --- | --- |
| `totalReceived` | koliko URL-ova je primljeno |
| `fetched` | koliko stranica je uspješno dohvaćeno |
| `parsed` | koliko ih je pročitano (parsirano) |
| `imported` | koliko **novih** proizvoda je spremljeno |
| `updated` | koliko **postojećih** (isti `externalId`) je ažurirano |
| `skipped` | greška dohvaćanja/čitanja ili import validacije |
| `needsReview` | parsirano, ali fali ključno polje — nije spremljeno kao usable |
| `errors` | po-URL greške (run se nastavlja) |
| `warnings` | meke napomene (fallback, korišteni defaults, nema slike…) |
| `products` | status po svakom URL-u |
| `reviewItems` | što točno popraviti za stavke koje trebaju pregled |

## Status po proizvodu

`fetched → parsed → imported | updated`, a inače `skipped` ili `needs-review`.

- **imported / updated** — proizvod je u katalogu i može ući u planner.
- **needs-review** — pročitan, ali fali npr. `price`, `category`, `roomTags` ili `styleTags`.
  Ne sprema se kao usable i ne ulazi u planner.
- **skipped** — stranica nije dostupna, nije se mogla pročitati ili je pala import validaciju
  (npr. `future-scraper` bez `productUrl`).

## Tipični warninzi

- „Korišten je fallback (OpenGraph/naslov)…” — nije bilo strukturiranih (JSON-LD) podataka.
- „Korišteni su zadani podaci za kategoriju/prostorije/stil…” — stranica ih ne daje.
- „Slika nije pronađena.”
- „Dostupnost nije pronađena, postavljena na provjeru u trgovini.”
- „Cijena je parsirana iz fallback izvora.”
- „Kvaliteta podataka nije potpuna (partial/needs-review).”

## Kako popraviti i ponoviti

Summary vraća i **`retryRequest`** (Sprint 9.5): gotov collector zahtjev koji sadrži **samo**
URL-ove koje treba ponoviti (needs-review i skipped), svaki sa svojim `defaults`. Kopiraj ga,
dopuni `defaults` prema `reviewItems` i pošalji ponovno — ne treba ručno slagati novi zahtjev.

1. Pogledaj `reviewItems`. Svaka stavka ima `url`, `missingFields` i `message`.
2. Ako fali `category` / `roomTags` / `styleTags` → dopuni `defaults` (ili per-item `defaults`
   u `items` obliku) i **ponovi zahtjev** s tim URL-om (najlakše kroz `retryRequest`).
3. Ako fali `price` → stranica vjerojatno ne izlaže cijenu strukturirano (JS-heavy ili druga
   shema). Ne forsiraj browser automatizaciju. Provjeri je li to pravi product URL; ako nema
   čitljive cijene, preskoči taj proizvod.
4. Ponovni import istog `externalId` **ažurira** postojeći proizvod (dedup), ne stvara
   duplikat — pa slobodno ponavljaj dok ne posložiš podatke.

## Sigurnost za planner

`needs-review` proizvodi i proizvodi bez cijene/kategorije/`roomTags`/`styleTags` **ne ulaze**
u plan. `unavailable` se ne bira. Stari / `check-store` / `limited` mogu ući, ali UI kaže
„Provjeri u trgovini”. Tako neprovjereni podaci ne mogu slučajno upasti u korisnikov plan.
