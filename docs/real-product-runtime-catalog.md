# Real product runtime catalog (Sprint 10.4)

Cilj: realni IKEA HR i JYSK HR **living-room** proizvodi (sa stvarnim product URL-ovima) moraju
biti u katalogu koji planner stvarno koristi — ne samo u docs/test JSON-u.

## Gdje je seed i kako se učitava

- **Izvor podataka:** `backend/src/main/resources/catalog/real-ikea-jysk-hr-living-room.json`
  (kopija production-pilot snapshota; 71 proizvod — IKEA 36, JYSK 35; sve living-room kategorije).
- **Loader:** `RealCatalogSeeder` (`ApplicationRunner`) na startu aplikacije:
  1. importa snapshot kroz postojeći import pipeline (`RetailerSnapshotImportService` →
     validacija + dedup po `externalId`), pa proizvodi dobiju stvarni `productUrl`,
  2. makne `living-room` tag s legacy sample proizvoda (oni iz `data.sql` nemaju
     `sourceReference`), tako da se living-room plan slaže **samo** od realnog kataloga.
- `data.sql` i dalje seeda ostale sobe / ostale trgovine (sample), to je izvan opsega 10.4.
- Seed je best-effort: ako snapshot fali, app nastavlja sa sample seedom.

## Kako pokrenuti

```bash
docker compose up -d db
cd backend && mvn spring-boot:run      # ili ./mvnw spring-boot:run ako wrapper postoji
npm --prefix frontend run dev
```

`ddl-auto: create` + `spring.sql.init.mode: always` znače da se na svaki start baza ponovno
napuni; `RealCatalogSeeder` se izvrši nakon `data.sql`.

## Kako provjeriti da "Otvori u trgovini" vodi na realni proizvod

1. Provjeri katalog: `curl http://localhost:8080/api/products | grep ikea.com` —
   living-room proizvodi imaju `productUrl` poput
   `https://www.ikea.com/hr/hr/p/...` ili `https://jysk.hr/dnevni-boravak/...`, ne homepage.
2. U aplikaciji upiši: `Imam 1500 € za dnevni boravak, moderno, najviše IKEA, već imam TV i tepih.`
3. Na product cardu vidiš naziv, trgovinu, cijenu i gumb **„Otvori u trgovini”**.
4. Klik na „Otvori u trgovini” otvara stvarni `productUrl` u novom tabu
   (`target="_blank"`, `rel="noopener noreferrer"`). Ako proizvod nema `productUrl`, gumb je
   onemogućen („Link će biti dostupan”).

## Testovi (offline)

- `RealProductRuntimeCatalogTest` — snapshot se importa, ≥20 IKEA i ≥20 JYSK proizvoda, svaki
  ima stvarni product URL (ne homepage); + provjera strip logike za `living-room`.
- `LivingRoomRealProductPlannerTest` — living-room plan koristi realne IKEA/JYSK proizvode sa
  stvarnim URL-ovima; `unavailable` i `needs-review` ne ulaze.
