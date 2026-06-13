# Real catalog smoke test

Ručni koraci za provjeru Sprinta 9.0 (real catalog source v1). Ovo nije scraper — uvozi se
pripremljeni JSON. Detalji formata su u [real-catalog-source.md](real-catalog-source.md).

## 1. Pokreni bazu i backend

```bash
docker compose up -d db
cd backend
./mvnw spring-boot:run
```

Ako nema `mvnw`, pokreni lokalnim Mavenom: `mvn spring-boot:run`.

## 2. Importaj real catalog source

Iz root foldera projekta:

```bash
curl -X POST http://localhost:8080/api/products/import/retailer-snapshot \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-real-catalog-source.json
```

Očekivanje: `created` ~40, `errors` prazan. Response ima `created`, `updated`, `skipped`,
`totalReceived`, `products`, `errors`.

## 3. Provjeri proizvode

```bash
curl http://localhost:8080/api/products
```

Provjeri da postoje proizvodi iz svih trgovina, npr. `rc-ikea-lr-sofa-light-01`,
`rc-jysk-of-chair-ergo-25`, `rc-decathlon-gym-dumbbell-33`.

## 4. Pokreni frontend i složi planove

```bash
npm --prefix frontend run dev
```

Upiši redom i provjeri da plan koristi proizvode iz kataloga:

- „Imam 1500 € za dnevni boravak, moderno.” → kauč, TV komoda, stolić, tepih, rasvjeta.
- „Radni kutak do 600 €, treba mi stol i stolica.” → radni stol + stolica (+ rasvjeta/polica).
- „Spavaća soba do 1200 €, najvažniji su krevet i madrac.” → krevet + madrac (+ udobnost).

## 5. Unavailable ne ulazi

Katalog ima namjerno nedostupne: `rc-ikea-bd-bed-frame-oak-21` i `rc-decathlon-gym-bike-35`
(`availabilityStatus: unavailable`). Složi spavaću sobu i kućnu teretanu — ti proizvodi ne
smiju ući u plan.

## 6. check-store / limited → „Provjeri u trgovini”

Proizvodi tipa `rc-pevex-lr-lamp-black-06` (check-store) i `rc-emmezeta-of-chair-comfort-26`
(limited) smiju ući, ali na kartici i u popisu stoji „Provjeri u trgovini”.

## 7. Stari podaci → „Provjeri u trgovini”

`rc-pevex-lr-rug-grey-11` i `rc-pevex-of-rug-small-32` imaju `lastCheckedAt` stariji od 14
dana. Ako uđu u plan, na kartici piše „Provjeri cijenu i dostupnost u trgovini.” iako su
`in-stock`.

## 8. „Već imam …” i „ne želim puno trgovina”

- „Dnevni boravak do 1500 €, već imam TV i tepih.” → TV komoda i tepih se ne dodaju.
- „Dnevni boravak do 1500 €, ne želim više od dvije trgovine.” → plan pokušava stati u ≤ 2
  trgovine i kaže „Većinu kupuješ u …”.

## 9. Bolji podaci imaju blagu prednost

Kad su dva proizvoda inače slična, planner blago preferira onaj s linkom, slikom, nedavnom
provjerom i `complete` kvalitetom. U UI-u se to ne vidi kao „score” — samo se pojavi bolji
proizvod.

## 10. Duplicate externalId updatea proizvod

Ponovno importaj isti katalog:

```bash
curl -X POST http://localhost:8080/api/products/import/retailer-snapshot \
  -H "Content-Type: application/json" \
  --data-binary @docs/sample-real-catalog-source.json
```

Očekivanje: sada proizvodi idu u `updated`, ne `created`. Broj proizvoda za isti
`externalId` se ne povećava, a `importedAt` se osvježi.
