# Real retailer pilot (dev-only)

Sprint 9.7. Konkretan workflow za prvi real retailer pilot: ručno odabran URL pack →
collector → review → import → catalog health → planner smoke test.

> **Nije** crawler, scheduler, admin UI ni masovno scrapiranje. URL pack je ručno održavan
> dev alat. Za produkciju preferiraj službene feedove / API-je / partnerstva. Ako retailer ne
> dopušta automatizirano dohvaćanje, **ne** koristi collector za tu trgovinu. Poštuj
> `robots.txt` i ToS.

## Koraci

### 1. Odaberi jedan pack
Ovisno o trgovini koju želiš testirati, odaberi pripremljen pack s pravim URL-ovima ili
placeholder pack i zamijeni URL-ove. Za dnevni boravak trenutno postoje dva stvarna
pilota (IKEA i JYSK) te nekoliko placeholdera:

* **Spreman stvarni pilot (IKEA HR, dnevni boravak):**
  [pilot-packs/real-ikea-hr-living-room-pilot.json](pilot-packs/real-ikea-hr-living-room-pilot.json)
  — 12 stvarnih `www.ikea.com/hr/hr` product URL-ova (kauč, TV komoda, stolić, tepih,
  rasvjeta, spremanje), svaki s vlastitim `category`, `roomTags`, `styleTags` i
  `sourceReference`. Ovaj se može poslati odmah.

* **Spreman stvarni pilot (JYSK HR, dnevni boravak):**
  [pilot-packs/real-jysk-hr-living-room-pilot.json](pilot-packs/real-jysk-hr-living-room-pilot.json)
  — 10–14 stvarnih `jysk.hr` product URL-ova za dnevni boravak (kauči, stol, tepih,
  rasvjeta, spremanje, dekoracije, fotelja). Svaki item ima vlastiti `category`,
  `roomTags`, `styleTags` i `sourceReference`. Nakon što je IKEA katalog provučen,
  ovaj pack omogućuje planneru da kombinira IKEA i JYSK proizvode.

* **Placeholder packovi (zamijeni URL-ove):**
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

Za JYSK pilot koristi datoteku `real-jysk-hr-living-room-pilot.json` na isti način.
Primjer:

```bash
curl -X POST http://localhost:8080/api/products/collect/retailer-urls \
  -H "Content-Type: application/json" \
  --data-binary @docs/pilot-packs/real-jysk-hr-living-room-pilot.json
```

Savjet: ako planiraš kombinirati IKEA i JYSK proizvode, prvo pokreni IKEA pack i pričekaj
da se proizvodi importaju. Nakon toga pošalji JYSK pack. Planner može preferirati jednu
trgovinu (npr. „najviše IKEA i JYSK”), ali mora imati dovoljan izbor proizvoda iz obje.

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


## Sprint 10.3: production-pilot coverage

Nakon IKEA/JYSK URL packova, produkcijski pilot se ne širi odmah na treću trgovinu.
Prvo se koristi jedan širi living-room snapshot i realistična scenario matrica:

- `docs/catalog-snapshots/real-ikea-jysk-hr-living-room-production-snapshot.json`
- `docs/production-pilot/living-room-scenario-matrix.json`
- `backend/src/test/java/ai/budgetspace/planner/LivingRoomProductionScenarioMatrixTest.java`

Ovaj sloj je offline QA/dev fixture: služi da planner prođe IKEA-only, JYSK-only,
IKEA+JYSK mix, exclusion/preference, budžet, already-owned i locked-product scenarije bez
live dohvaćanja stranica. Prije javne produkcije cijene, slike i zalihe ponovno osvježi
collectorom ili službenim feedom.

```bash
cd backend
mvn test -Dtest=LivingRoomProductionScenarioMatrixTest,ProductTaxonomyTest
```

Detalji su u [production-pilot/living-room-production-pilot.md](production-pilot/living-room-production-pilot.md).

## Pregled prošlih runova (dev)

```bash
curl http://localhost:8080/api/products/collect/runs
curl http://localhost:8080/api/products/collect/runs/{runId}
```

Runovi se spremaju best-effort (ako baza nije dostupna, run i dalje radi, samo se ne spremi).
