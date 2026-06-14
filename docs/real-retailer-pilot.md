# Real retailer pilot (dev-only)

Sprint 9.7. Konkretan workflow za prvi real retailer pilot: ručno odabran URL pack →
collector → review → import → catalog health → planner smoke test.

> **Nije** crawler, scheduler, admin UI ni masovno scrapiranje. URL pack je ručno održavan
> dev alat. Za produkciju preferiraj službene feedove / API-je / partnerstva. Ako retailer ne
> dopušta automatizirano dohvaćanje, **ne** koristi collector za tu trgovinu. Poštuj
> `robots.txt` i ToS.

## Koraci

### 1. Odaberi jedan pack
- **Spreman stvarni pilot (IKEA HR, dnevni boravak):**
  [pilot-packs/real-ikea-hr-living-room-pilot.json](pilot-packs/real-ikea-hr-living-room-pilot.json)
  — 12 stvarnih `www.ikea.com/hr/hr` product URL-ova (kauč, TV komoda, stolić, tepih,
  rasvjeta, spremanje), svaki s vlastitim `category` / `roomTags` / `styleTags` /
  `sourceReference`. Ovaj se može poslati odmah.
- **Placeholder packovi (zamijeni URL-ove):**
  [pilot-packs/ikea-living-room.json](pilot-packs/ikea-living-room.json),
  [pilot-packs/ikea-bedroom.json](pilot-packs/ikea-bedroom.json),
  [pilot-packs/ikea-home-office.json](pilot-packs/ikea-home-office.json).

### 2. Zamijeni placeholder URL-ove (samo placeholder packovi)
Za placeholder packove zamijeni `example-ikea.com` URL-ove **stvarnim product URL-ovima koje
smiješ dohvaćati**.

**Odgovorno korištenje (obavezno):**
- ovo je dev-only alat, **ne** za masovno skidanje stranica,
- ne koristi ga ako stranica zabranjuje automatizirano dohvaćanje; poštuj `robots.txt` i ToS,
- za produkciju preferiraj službene feedove / API-je / partnerstva,
- **prvo probaj s 5–6 URL-ova**, pa tek onda pošalji ostatak,
- ako requestovi budu blokirani (403/429), **ne pokušavaj bypass** — stani i koristi drugi izvor.

### 3. Pokreni collector
Stvarni IKEA HR pilot pack:
```bash
curl -X POST http://localhost:8080/api/products/collect/retailer-urls \
  -H "Content-Type: application/json" \
  --data-binary @docs/pilot-packs/real-ikea-hr-living-room-pilot.json
```
(Za placeholder packove zamijeni naziv datoteke, npr. `ikea-living-room.json`.)

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
Pokreni frontend (`npm --prefix frontend run dev`) i upiši (prompt za stvarni IKEA HR pilot):
```text
Imam 1500 € za dnevni boravak, moderno, najviše IKEA, već imam TV i tepih.
```
Očekivano: planner preferira IKEA, ne dodaje TV komodu ni tepih (jer si rekao da ih već imaš),
a kauč i ostalo dolaze iz prikupljenih proizvoda.

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
