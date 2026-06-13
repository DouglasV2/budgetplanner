# Retailer snapshot import

Sprint 8.4. Ovo je prvi korak prema stvarnijim proizvodima iz trgovina.

> Sprint 9.0: ovaj isti import sada podržava i `lastCheckedAt` te source metadata
> (`sourceType`, `sourceName`, `sourceReference`, `dataQuality`, `dataQualityNotes`). Za
> realniji izvor s URL-ovima, slikama, dostupnošću i svježinom vidi
> [real-catalog-source.md](real-catalog-source.md).

**Ovo nije pravi scraper.** Nema Playwrighta, Seleniuma, HTML parsiranja ni živih poziva na IKEA / JYSK / Lesnina / Emmezeta / Pevex / Decathlon stranice. Umjesto toga radimo s **kontroliranim snapshotom**: ručno pripremljenom JSON listom proizvoda koja izgleda kao izvoz iz trgovine. To je priprema za buduće stvarne trgovine i kasnije adaptere, bez da sada radimo scraping.

Snapshot ulazi kroz isti import pipeline kao i obični JSON/CSV import iz Sprinta 8.3: ista validacija, isto mapiranje kategorija/stilova/prostorija i ista deduplikacija po `externalId`.

## Kako to radi iznutra

```
retailer snapshot JSON
        │
        ▼
RetailerCatalogAdapter   →  preslika u interni ImportProductDto
        │
        ▼
ProductImportService     →  validacija + taksonomija + dedup po externalId
        │
        ▼
Product (planner katalog)
```

Sva pravila o tome što je valjan proizvod ostaju na jednom mjestu (`ProductImportService`), pa se snapshot proizvodi ponašaju isto kao ručni import.

## Format snapshota

Snapshot je JSON lista. Svaki proizvod podržava:

| Polje | Obavezno | Napomena |
| --- | --- | --- |
| `externalId` | da | Jedinstveni ključ trgovine. Po njemu se radi dedup. |
| `name` | da | Naziv proizvoda. |
| `retailer` | da | IKEA, JYSK, Pevex, Emmezeta, Decathlon ili Lesnina. |
| `category` | da | Smije biti hrvatski sinonim (`tv komoda`, `stolić`, `lampa`, `krevet`, `stol`…). Normalizira se u canonical kategoriju. |
| `price` | da | Mora biti veći od 0. |
| `roomTags` | da | Smiju biti sinonimi (`dnevni boravak`, `spavaća soba`, `radni kutak`, `kućna teretana`). |
| `styleTags` | da | Smiju biti sinonimi (`moderno`, `clean`…). |
| `productUrl` | ne | Ako postoji, mora izgledati kao URL. |
| `imageUrl` | ne | Ako postoji, mora izgledati kao URL. |
| `availabilityStatus` | ne | `in-stock`, `limited`, `unavailable` ili `check-store`. Default je `in-stock`. |
| `deliveryNote` | ne | Slobodan tekst o dostavi/preuzimanju. |
| `priceTier` | ne | `budget`, `standard` ili `premium`. Ako nema, izračuna se iz cijene. |

Detalji o sinonimima kategorija i stilova su u [product-import.md](product-import.md).

Gotov primjer je u [sample-retailer-snapshot.json](sample-retailer-snapshot.json) — proizvodi iz svih šest trgovina, generički nazivi tipa „Dvosjed svijetli tekstil”, „TV komoda hrast efekt”, „Set bučica 20 kg”.

## Kako pripremiti snapshot ručno

1. Otvori novi `.json` i napravi listu `[]`.
2. Za svaki proizvod kopiraj blok s poljima gore. Najsigurnije je krenuti od jednog reda iz `sample-retailer-snapshot.json`.
3. Stavi stvaran-izgledajući `externalId` (npr. šifru iz trgovine). Isti `externalId` kasnije ažurira proizvod, ne stvara duplikat.
4. Provjeri da svaki proizvod ima `roomTags` i `styleTags` — bez njih proizvod ne može ući u planner.
5. Označi dostupnost: `unavailable` proizvodi neće ući u plan; `limited` i `check-store` smiju ući, ali planner ih označi s „Provjeri u trgovini”.

## Import preko curl-a

Pokreni backend, zatim iz root foldera projekta:

```bash
curl -X POST http://localhost:8080/api/products/import/retailer-snapshot \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-retailer-snapshot.json
```

## Primjer import summary responsea

Import ne pada ako je jedan proizvod loš — taj se preskoči, ostatak se importira ili ažurira po `externalId`.

```json
{
  "created": 12,
  "updated": 0,
  "skipped": 0,
  "totalReceived": 12,
  "products": [
    {
      "externalId": "retailer-snap-ikea-sofa-light-textile",
      "name": "Dvosjed svijetli tekstil",
      "retailer": "IKEA",
      "category": "sofa",
      "price": 299.0,
      "availabilityStatus": "in-stock"
    }
  ],
  "errors": []
}
```

Ako neki proizvod ima grešku, ona se vrati u `errors` (s `row` i `externalId`), a `skipped` se poveća.

## Kako provjeriti da proizvodi ulaze u planner

1. Importaj snapshot (curl gore).
2. Provjeri katalog:

   ```bash
   curl http://localhost:8080/api/products
   ```

   Trebaš vidjeti npr. `retailer-snap-ikea-sofa-light-textile` s `category: "sofa"` i `roomTags: ["living-room"]` (normalizirano iz „kauč” i „dnevni boravak”).
3. U planneru složi plan za dnevni boravak. Snapshot proizvodi s `living-room` tagom mogu ući u plan (kauč, TV komoda, stolić, tepih, rasvjeta…).
4. `retailer-snap-pevex-indoor-bike-unavailable` je namjerno `unavailable` — ne smije ući u plan.

Puni redoslijed koraka je u [import-smoke-test.md](import-smoke-test.md).
