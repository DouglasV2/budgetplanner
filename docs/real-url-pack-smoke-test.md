# Real URL pack smoke test (dev-only)

Sprint 9.4. Mali, kontrolirani „real catalog proof”: ručno odabran set product URL-ova →
collector → import summary → `/api/products` → planner → korisnik vidi realniji plan.

> **Ovo nije** crawler, nije masovno dohvaćanje, nije production scraping, nije frontend UI.
> URL pack je ručno održavan dev alat. Za produkciju preferiraj službene feedove / API-je /
> partnerstva. Ako retailer ne dopušta automatizirano dohvaćanje, **ne** koristi collector.

## 1. Pokreni bazu i backend

```bash
docker compose up -d db
cd backend
./mvnw spring-boot:run
```

Ako nema `mvnw`, koristi `mvn spring-boot:run`.

## 2. Uzmi curated URL pack

[curated-ikea-living-room-url-pack.json](curated-ikea-living-room-url-pack.json) koristi
`items` oblik, gdje svaki URL ima svoj `category`/`styleTags` (pa jedan pack pokriva kauč,
TV komodu, stolić, tepih, rasvjetu, spremanje i dekoracije bez krivog globalnog defaulta).

**Zamijeni placeholder `example-ikea.com` URL-ove stvarnim product URL-ovima koje smiješ
testirati.** Ne testiraj URL-ove za koje nisi siguran da se smiju automatizirano dohvaćati.

## 3. Pošalji collector zahtjev

```bash
curl -X POST http://localhost:8080/api/products/collect/retailer-urls \
  -H "Content-Type: application/json" \
  --data-binary @docs/curated-ikea-living-room-url-pack.json
```

## 4. Pročitaj summary

Provjeri `imported`, `updated`, `skipped`, `needsReview`, `warnings` i `products` (status po
URL-u). Detalji: [collector-review-workflow.md](collector-review-workflow.md).

## 5. Popravi needs-review i ponovi

Za stavke u `reviewItems` dopuni `defaults` (npr. fali `category`/`roomTags`/`styleTags`) i
**ponovi** zahtjev. Isti `externalId` ažurira proizvod (dedup), ne stvara duplikat.

## 6. Provjeri katalog

```bash
curl http://localhost:8080/api/products
```

## 7. Složi plan i provjeri ponašanje

Pokreni frontend (`npm --prefix frontend run dev`) i upiši:

```text
Imam 1500 € za dnevni boravak, moderno, ne želim više od dvije trgovine.
```

Provjeri:

- proizvodi iz packa mogu ući u plan (kauč, TV komoda, stolić, tepih, rasvjeta…),
- **unavailable** proizvodi ne ulaze,
- **stale / check-store / limited** proizvodi prikazuju „Provjeri u trgovini”,
- **needs-review** proizvodi ne ulaze u plan,
- duplicate `externalId` ažurira proizvod (ponovni run),
- „već imam TV i tepih” → planner ne dodaje TV komodu ni tepih,
- „najviše IKEA” → planner preferira IKEA kad ima smisla.

## Što ovo nije

Nema crawlera kategorija, nema schedulera, nema masovnog skidanja, nema admin/collector UI-a.
Sve ostaje backend/dev docs workflow nad malim, ručno odabranim listama URL-ova.
