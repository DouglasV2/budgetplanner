# Import smoke test

Ručni koraci za provjeru importa i planera (Sprint 8.3 + 8.4).

## 1. Pokreni bazu i backend

Iz root foldera projekta:

```bash
docker compose up -d db
cd backend
./mvnw spring-boot:run
```

Ako nema `mvnw`, pokreni backend lokalnim Mavenom:

```bash
cd backend
mvn spring-boot:run
```

## 2. Importaj sample JSON katalog

Iz root foldera projekta:

```bash
curl -X POST http://localhost:8080/api/products/import \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-products-import.json
```

Provjeri da response ima `created`, `updated`, `skipped`, `totalReceived`, `products` i `errors`.

## 3. Importaj sample CSV katalog

```bash
curl -X POST http://localhost:8080/api/products/import/csv \
  -H "Content-Type: text/csv" \
  --data-binary @docs/sample-products-import.csv
```

CSV sadrži `sample-ikea-living-sofa-light-textile`, koji već postoji u JSON sampleu. Očekivanje: taj proizvod ide u `updated`, ne stvara se novi proizvod.

## 4. Importaj sample retailer snapshot

```bash
curl -X POST http://localhost:8080/api/products/import/retailer-snapshot \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-retailer-snapshot.json
```

Očekivanje: `created` raste, `errors` je prazan. Snapshot koristi hrvatske sinonime kategorija (`tv komoda`, `stolić`, `lampa`, `krevet`, `stol`, `komoda`, `oprema za vježbanje`) i prostorija (`dnevni boravak`…), pa ovo ujedno provjerava normalizaciju. Detalji su u [retailer-snapshot-import.md](retailer-snapshot-import.md).

## 5. Provjeri proizvode

```bash
curl http://localhost:8080/api/products
```

Provjeri da postoje proizvodi iz svih importa, npr.:

- `sample-ikea-living-sofa-light-textile` (JSON)
- `csv-pevex-office-lamp-black` (CSV)
- `retailer-snap-ikea-sofa-light-textile` — `category: "sofa"`, `roomTags: ["living-room"]` (normalizirano iz „kauč” / „dnevni boravak”)
- `retailer-snap-lesnina-tv-unit-oak` — `category: "tv-unit"` (normalizirano iz „tv komoda”)

## 6. Složi plan za dnevni boravak

Pokreni frontend:

```bash
npm --prefix frontend run dev
```

U planner upiši:

```text
Imam 1500 € za dnevni boravak, želim moderno i ne želim obilaziti puno trgovina.
```

Očekivanje: u plan mogu ući proizvodi iz importa s `living-room` tagom, npr. kauč, TV komoda, stolić, tepih ili rasvjeta — uključujući snapshot proizvode (`retailer-snap-*`).

## 7. Provjeri da plan prikazuje broj trgovina i ukupno po trgovini

U rezultatu plana provjeri:

- U kartici plana stoji broj trgovina (npr. „Trgovine: 2”) i rečenica tipa „Većinu kupuješ u …”.
- „Popis za kupnju” je grupiran po trgovini, sa zbrojem po trgovini i ukupnom cijenom.
- Iznad popisa stoji kratka rečenica tipa „Sve bitno možeš kupiti u 2 trgovine za …”.

## 8. Provjeri „Provjeri u trgovini” za limited/check-store

Snapshot ima `limited` i `check-store` proizvode (npr. `retailer-snap-pevex-floor-lamp-black` = `check-store`, `retailer-snap-lesnina-storage-low` = `limited`).

Očekivanje: takvi proizvodi smiju ući u plan, ali su u popisu i na kartici označeni s „Provjeri u trgovini”.

## 9. Provjeri da unavailable proizvod ne ulazi

Postoje namjerno nedostupni proizvodi:

```text
sample-decathlon-gym-bike-unavailable   (JSON sample)
retailer-snap-pevex-indoor-bike-unavailable   (snapshot sample)
```

Složi plan za kućnu teretanu:

```text
Imam 600 € za kućnu teretanu, želim moderno.
```

Očekivanje: nijedan `unavailable` proizvod ne ulazi u plan. `limited` i `check-store` smiju ući, ali uz „Provjeri u trgovini”.

## 10. Provjeri da proizvodi bez dobrih tagova ne ulaze

Import validacija mora preskočiti proizvod bez `roomTags` ili `styleTags`. Planner dodatno ne bira proizvod koji nema room tag za traženu prostoriju.

## 11. Provjeri duplicate externalId update

Ponovno importaj snapshot:

```bash
curl -X POST http://localhost:8080/api/products/import/retailer-snapshot \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-retailer-snapshot.json
```

Očekivanje: sada proizvodi idu u `updated`, ne `created`. Broj proizvoda za isti `externalId` se ne povećava. Isto vrijedi za ponovni JSON/CSV import.
