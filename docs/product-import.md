# Product import

Sprint 8 koristi siguran import sloj prije pravih scrapera. Ideja je da katalog možemo puniti ručno, iz CSV/JSON datoteke ili iz prvog malog retailer adaptera, bez promjene recommendation enginea.

## JSON import

```bash
curl -X POST http://localhost:8080/api/products/import \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-products-import.json
```

Response vraća sažetak:

```json
{
  "created": 40,
  "updated": 0,
  "skipped": 0,
  "errors": [],
  "products": []
}
```

## CSV import

Endpoint prima CSV s headerom. Obavezna polja su `externalId`, `name`, `retailer`, `category` i `price`.

```bash
curl -X POST http://localhost:8080/api/products/import/csv \
  -H "Content-Type: text/csv" \
  --data-binary @docs/sample-products-import.csv
```

Podržani headeri uključuju i hrvatske nazive poput `naziv`, `trgovina`, `kategorija`, `cijena`, `stilovi`, `prostorije`, `link` i `slika`.

## IKEA starter adapter

Ovo još nije pravi scraper. Endpoint ubacuje mali kurirani IKEA starter set kroz isti import pipeline:

```bash
curl -X POST http://localhost:8080/api/products/import/ikea-starter
```

To služi da provjerimo kako će budući retailer adapteri puniti `externalId`, `productUrl`, `imageUrl`, `availabilityStatus` i `lastCheckedAt`.

## Validacija

Import preskače proizvod ako nedostaje:

- `externalId`
- `name`
- `retailer`
- `category`
- `price`

Preskočeni proizvodi ulaze u `skipped`, a razlog ide u `errors`.
