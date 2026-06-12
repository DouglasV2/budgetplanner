# Import smoke test

Ovo su ručni koraci za provjeru Sprinta 8.3.

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

## 4. Provjeri proizvode

```bash
curl http://localhost:8080/api/products
```

Provjeri da postoje proizvodi iz importa, npr.:

- `sample-ikea-living-sofa-light-textile`
- `sample-decathlon-gym-dumbbell-set`
- `csv-pevex-office-lamp-black`

## 5. Složi plan za dnevni boravak

Pokreni frontend:

```bash
npm --prefix frontend run dev
```

U planner upiši:

```text
Imam 1500 € za dnevni boravak, želim moderno i ne želim obilaziti puno trgovina.
```

Očekivanje: u plan mogu ući proizvodi iz importa s `living-room` tagom, npr. kauč, TV komoda, stolić, tepih ili rasvjeta.

## 6. Provjeri da proizvodi bez dobrih tagova ne ulaze

Import validacija mora preskočiti proizvod bez `roomTags` ili `styleTags`. Planner dodatno ne bira proizvod koji nema room tag za traženu prostoriju.

## 7. Provjeri da unavailable proizvod ne ulazi

U sample JSON katalogu postoji:

```text
sample-decathlon-gym-bike-unavailable
```

Složi plan za kućnu teretanu:

```text
Imam 600 € za kućnu teretanu, želim moderno.
```

Očekivanje: `sample-decathlon-gym-bike-unavailable` ne ulazi u plan. `limited` i `check-store` proizvodi smiju ući, ali korisnik treba vidjeti “Provjeri u trgovini”.

## 8. Provjeri duplicate externalId update

Ponovno importaj CSV:

```bash
curl -X POST http://localhost:8080/api/products/import/csv \
  -H "Content-Type: text/csv" \
  --data-binary @docs/sample-products-import.csv
```

Očekivanje: proizvodi iz CSV-a sada uglavnom idu u `updated`. Ne smije se povećati broj proizvoda za isti `externalId`.
