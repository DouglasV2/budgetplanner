# Real retailer pilot (dev-only)

Sprint 9.7. Konkretan workflow za prvi real retailer pilot: ručno odabran URL pack →
collector → review → import → catalog health → planner smoke test.

> **Nije** crawler, scheduler, admin UI ni masovno scrapiranje. URL pack je ručno održavan
> dev alat. Za produkciju preferiraj službene feedove / API-je / partnerstva. Ako retailer ne
> dopušta automatizirano dohvaćanje, **ne** koristi collector za tu trgovinu. Poštuj
> `robots.txt` i ToS.

## Koraci

### 1. Odaberi jedan pack
[pilot-packs/ikea-living-room.json](pilot-packs/ikea-living-room.json),
[pilot-packs/ikea-bedroom.json](pilot-packs/ikea-bedroom.json) ili
[pilot-packs/ikea-home-office.json](pilot-packs/ikea-home-office.json). Svaki koristi `items`
oblik gdje svaki URL ima svoj `category` / `roomTags` / `styleTags`.

### 2. Zamijeni placeholder URL-ove
Zamijeni `example-ikea.com` URL-ove **stvarnim product URL-ovima koje smiješ testirati**. Ne
testiraj URL-ove za koje nisi siguran da se smiju automatizirano dohvaćati.

### 3. Pokreni collector
```bash
curl -X POST http://localhost:8080/api/products/collect/retailer-urls \
  -H "Content-Type: application/json" \
  --data-binary @docs/pilot-packs/ikea-living-room.json
```

Collector v10 donosi dodatne sigurnosne mjere:

- **Allowlist domena** – URL-ovi moraju biti na dopuštenoj domeni za odabranu trgovinu
  (npr. `ikea.com`, `ikea.hr`). Linkovi na druge domene se preskaču s jasnom porukom.
- **Delay između requestova** – između svakog dohvaćanja stranice čeka se pola
  sekunde kako bi se serveri poštedjeli naglog opterećenja.
- **Jasne greške za HTTP 4xx/5xx** – statusi 403, 404 i slični prikazuju se u
  sažetku, a run se nastavlja za ostale URL-ove.

### 4. Pročitaj summary
Pogledaj `imported`, `updated`, `skipped`, `needsReview`, `warnings` i `products` (status po
URL-u). Vidi [collector-review-workflow.md](collector-review-workflow.md).

### 5. Popravi retry request
Summary vraća `retryRequest` — gotov zahtjev sa samo onim URL-ovima koje treba ponoviti
(needs-review / skipped). Kopiraj ga, dopuni `defaults` po `reviewItems` i spremi u datoteku.

### 6. Ponovno pokreni
Pošalji popravljeni `retryRequest`. Isti `externalId` ažurira proizvod (dedup), ne stvara
duplikat.

### 7. Provjeri catalog health
```bash
curl http://localhost:8080/api/products/catalog-health
```
Provjeri `plannerReadiness` za sobu — je li `ready: true` i koje su `weakCategories`. Detalji:
[catalog-health.md](catalog-health.md).

### 8. Složi plan u aplikaciji
Pokreni frontend (`npm --prefix frontend run dev`) i upiši:
```text
Imam 1500 € za dnevni boravak, moderno, ne želim više od dvije trgovine.
```

### 9. Ručno provjeri
- proizvodi imaju cijene,
- „Otvori u trgovini” vodi na pravi link,
- `unavailable` ne ulazi u plan,
- `check-store` / `limited` / stari proizvodi pokazuju „Provjeri u trgovini”,
- ako fali osnovna kategorija (npr. nema kauča), UI kaže da je **plan djelomičan**,
- „već imam TV i tepih” stvarno makne TV komodu i tepih,
- „najviše IKEA” stvarno preferira IKEA.

## Pregled prošlih runova (dev)

```bash
curl http://localhost:8080/api/products/collect/runs
curl http://localhost:8080/api/products/collect/runs/{runId}
```

Runovi se spremaju best-effort (ako baza nije dostupna, run i dalje radi, samo se ne spremi).
