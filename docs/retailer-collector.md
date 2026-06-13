# Retailer collector (dev-only)

Sprint 9.1. Prvi **kontrolirani** korak prema stvarnim podacima iz trgovina.

> **Dev-only alat.** Nema korisničkog UI-a i nije za masovno korištenje. Korisnik nikad ne
> vidi kako je proizvod prikupljen — vidi samo proizvod, cijenu, trgovinu i linkove.

## Što ovo jest, a što nije

**Jest:** uzme malu, eksplicitnu listu URL-ova proizvoda, pokuša pročitati osnovne podatke
(naziv, cijena, slika, dostupnost), normalizira ih u postojeći real catalog format iz 9.0 i
provuče kroz **postojeću validaciju i dedup po `externalId`**.

**Nije:**
- nije full crawler — ne obilazi kategorije, search ni paginaciju,
- ne koristi Playwright, Selenium, headless browser ni JS rendering,
- ne skida stranice masovno ni paralelno.

Ako je stranica JS-heavy i ne izloži ništa strukturirano, collector vrati `needs-review` /
`partial` rezultat ili grešku — **ne** poseže za browser automatizacijom.

## Odgovorno korištenje

- Koristi **male liste URL-ova** (limit je 20 po zahtjevu).
- Poštuj pravila trgovina, `robots.txt` i uvjete korištenja (ToS).
- Ako se trgovina protivi automatiziranom dohvaćanju, **ne** koristi collector za tu trgovinu.
- Za produkciju preferiraj **službene feedove / API-je / partnerstva** gdje je moguće.
- Dohvaćanje je sekvencijalno, s timeoutom i običnim user-agentom; ne forsiraj velike runove.

## Kako radi iznutra

```
mala lista URL-ova
      │
      ▼
ProductPageFetcher        →  jedan GET po URL-u (timeout, user-agent, ograničeni redirecti)
      │
      ▼
RetailerProductParser     →  JSON-LD Product → OpenGraph/meta → <title> → defaults iz zahtjeva
      │
      ▼
RetailerSnapshotImportService → ista validacija + dedup po externalId (Sprint 8.4 / 9.0)
      │
      ▼
Product (planner katalog)
```

Parser pokušava redom: **JSON-LD** `Product` schema → **OpenGraph/meta** tagovi → HTML
`<title>` → **defaults** iz zahtjeva za `category` / `roomTags` / `styleTags`.

Ako **cijena** ne može biti pročitana, proizvod se **ne uvozi** — završi kao `skipped` uz
jasnu grešku. Ako `category`/`roomTags`/`styleTags` nisu sigurni, koristi se `defaults`; ako
ni njih nema, vrati se jasna greška.

Prikupljeni proizvod dobiva `sourceType: "future-scraper"`, `lastCheckedAt` na danas i
`dataQuality` `partial` (JSON-LD) ili `needs-review` (samo OpenGraph/title). U UI-u se to ne
prikazuje tehnički — stari/nesigurni podaci samo dobiju „Provjeri u trgovini”.

## Zahtjev

```json
{
  "retailer": "IKEA",
  "urls": [
    "https://www.example-ikea.com/hr/p/dvosjed-svijetli-tekstil-123",
    "https://www.example-ikea.com/hr/p/tv-komoda-hrast-456"
  ],
  "defaults": { "category": "sofa", "roomTags": ["living-room"], "styleTags": ["modern"], "sourceReference": "collector-demo-2026-06" }
}
```

> URL-ovi u [sample-retailer-url-collector-request.json](sample-retailer-url-collector-request.json)
> su placeholderi (`example-ikea.com`). Zamijeni ih stvarnim URL-ovima samo ako smiješ
> dohvaćati tu trgovinu. Testovi **ne** dohvaćaju internet — koriste offline HTML fixture.

## Pokretanje (curl)

```bash
curl -X POST http://localhost:8080/api/products/collect/retailer-urls \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-retailer-url-collector-request.json
```

## Odgovor (run summary)

```json
{
  "totalReceived": 2,
  "collected": 2,
  "imported": 2,
  "skipped": 0,
  "errors": [],
  "importSummary": {
    "created": 2,
    "updated": 0,
    "skipped": 0,
    "totalReceived": 2,
    "products": [ { "externalId": "collected-ikea-dvosjed-svijetli-tekstil-123", "retailer": "IKEA", "category": "sofa", "price": 299.0 } ],
    "errors": []
  }
}
```

Ako jedan URL padne (mreža, parsiranje, nema cijene), pojavi se u `errors`, a ostatak runa se
nastavi. Ponovno prikupljanje istog URL-a **ažurira** postojeći proizvod (dedup po
`externalId`), ne stvara duplikat.

## Kako kasnije proširiti

Retailer-specific parsiranje drži se malim i pokrivenim fixtureima (vidi
`IkeaProductParser` kao skeleton). Pravi feed/scraper kasnije treba samo proizvesti isti
oblik podataka — planner, validacija i dedup ostaju nepromijenjeni.
