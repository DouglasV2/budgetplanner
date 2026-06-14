# Retailer collector (dev-only)

Sprint 9.1–9.2. Kontrolirani alat koji uzme **malu, eksplicitnu listu product URL-ova**,
pokuša pročitati osnovne podatke i provuče ih kroz **postojeću validaciju i dedup** (Sprint
9.0). Korisnik nikad ne vidi kako je proizvod prikupljen — vidi samo proizvod, cijenu,
trgovinu i linkove.

> **Dev-only.** Nema korisničkog UI-a. Nije za masovno korištenje.

## Što ovo jest, a što nije

**Jest:** mala lista URL-ova → fetch → čitanje podataka → import kroz postojeći pipeline.

**Nije:** nije crawler (bez kategorija, searcha, paginacije), nema schedulera, nema
Playwrighta/Seleniuma/headless browsera/JS renderinga, nema masovnog/paralelnog skidanja.

Ako je stranica JS-heavy i ne izloži ništa strukturirano, rezultat je `needs-review` /
`partial` ili greška — nikad browser automatizacija.

## Odgovorno korištenje

- Male liste URL-ova (limit **20** po zahtjevu), sekvencijalno, s timeoutom i običnim user-agentom.
- Poštuj pravila trgovina, `robots.txt` i ToS. Ako se trgovina protivi automatiziranom
  dohvaćanju, **ne** koristi collector za tu trgovinu.
- Za produkciju preferiraj **službene feedove / API-je / partnerstva**.
- Testovi koriste **offline HTML fixture**, nikad živi internet.

## Kako radi iznutra

```
mala lista URL-ova
      │  ProductPageFetcher (jedan GET po URL-u, timeout, user-agent, ograničeni redirecti)
      ▼  RetailerProductParser: JSON-LD Product → retailer-specific (IKEA v1) → OpenGraph/meta → <title> → defaults
RetailerCollectorService → RetailerSnapshotImportService → ista validacija + dedup po externalId
      ▼
Product (planner katalog)
```

Retailer-specific parser v1 (IKEA) opisan je u [retailer-parser-v1.md](retailer-parser-v1.md).
Kako čitati rezultat i popraviti `needs-review` opisano je u
[collector-review-workflow.md](collector-review-workflow.md). Cijeli pilot workflow (pack →
collector → catalog health → planner) je u [real-retailer-pilot.md](real-retailer-pilot.md).

Summary vraća i `retryRequest` (gotov zahtjev za ponavljanje samo needs-review/skipped
stavki). Runovi se best-effort spremaju i čitaju preko dev endpointa
`GET /api/products/collect/runs` i `GET /api/products/collect/runs/{runId}`. Je li katalog
dovoljno dobar za planner provjeri preko `GET /api/products/catalog-health`
([catalog-health.md](catalog-health.md)).


## Sprint 10.3: od collectora do production-pilot matrice

Collector i dalje ostaje mali, ručni/dev alat. Za produkcijski smjer nakon IKEA/JYSK URL
packova koristi se offline production-pilot snapshot + scenario matrix, a ne masovno
skidanje stranica. Snapshot se može osvježiti collectorom ili službenim feedom, zatim se
prije deploya vrte catalog health i `LivingRoomProductionScenarioMatrixTest`.

Ključno pravilo: parsiran proizvod bez pozitivne cijene može završiti kao `needs-review` ili
`skipped`, ali ne smije postati planner-usable.

## Zahtjev — dva oblika

**A) `urls` + globalni `defaults`:**

```json
{
  "retailer": "IKEA",
  "urls": ["https://www.example-ikea.com/hr/p/dvosjed-123"],
  "defaults": { "category": "sofa", "roomTags": ["living-room"], "styleTags": ["modern"] }
}
```

**B) `items` s vlastitim `defaults` po URL-u** (Sprint 9.4) — da jedan zahtjev može miješati
kategorije (kauč, TV komoda, tepih…) bez krivog globalnog `category`:

```json
{
  "retailer": "IKEA",
  "items": [
    { "url": "https://www.example-ikea.com/hr/p/dvosjed-123", "defaults": { "category": "sofa", "roomTags": ["living-room"], "styleTags": ["modern"] } },
    { "url": "https://www.example-ikea.com/hr/p/tv-komoda-456", "defaults": { "category": "tv-unit", "roomTags": ["living-room"], "styleTags": ["minimal"] } }
  ]
}
```

Ako su `items` prisutni, koriste se oni (per-item `defaults` pobjeđuju, a što izostave
nasljeđuju od request-level `defaults`). Inače se koriste `urls` + globalni `defaults`.
Stari `urls` oblik i dalje radi.

## Pokretanje (curl)

```bash
curl -X POST http://localhost:8080/api/products/collect/retailer-urls \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-ikea-url-collector-request.json
```

## Odgovor (run summary v2)

```json
{
  "runId": "f1c2…",
  "startedAt": "2026-06-13T10:00:00Z",
  "finishedAt": "2026-06-13T10:00:03Z",
  "retailer": "IKEA",
  "totalReceived": 4,
  "fetched": 3,
  "parsed": 3,
  "imported": 1,
  "updated": 1,
  "skipped": 1,
  "needsReview": 1,
  "errors": [ { "url": "https://…/broken", "message": "Stranica nije dostupna." } ],
  "warnings": [ "Korišten je fallback (OpenGraph/naslov) jer strukturirani podaci nisu potpuni." ],
  "importSummary": { "created": 1, "updated": 1, "skipped": 0, "totalReceived": 2, "products": [], "errors": [] },
  "products": [
    { "url": "https://…/sofa-1", "externalId": "ikea-30299118", "name": "Dvosjed BJORK svijetli", "status": "imported", "dataQuality": "complete", "message": "OK", "warnings": [] },
    { "url": "https://…/lack",   "externalId": "ikea-20011408", "name": "Stolić LACK bijeli", "status": "needs-review", "dataQuality": "needs-review", "message": "Nedostaje: price. Dopuni defaults i ponovi zahtjev.", "warnings": ["Slika nije pronađena."] }
  ],
  "reviewItems": [
    { "url": "https://…/lack", "suggestedExternalId": "ikea-20011408", "suggestedName": "Stolić LACK bijeli", "missingFields": ["price"], "message": "Nedostaje: price. Dopuni defaults i ponovi zahtjev." }
  ]
}
```

### Status po proizvodu

`fetched` → `parsed` → `imported` (novi) ili `updated` (postojeći `externalId`), a inače
`skipped` (greška ili import validacija) ili `needs-review` (parsirano, ali fali ključno
polje). Loš URL ne ruši cijeli run.

### Što ne ulazi u planner

- proizvod bez cijene, kategorije, `roomTags` ili `styleTags` (ne uvozi se kao usable),
- `future-scraper` rezultat bez `productUrl` (odbijen u importu),
- `unavailable` (sprema se, ali planner ga ne bira),
- `dataQuality: needs-review` (sprema se samo ako prođe validaciju, ali planner ga ne bira
  dok se ne popravi).

`limited` / `check-store` / stari proizvodi mogu ući, ali UI kaže „Provjeri u trgovini”.
