# Production launch runbook (Sprint 10.5)

Practical steps to run BudgetSpace AI and hand it to the first real users. Scope of the pilot:
**living-room only**, real **IKEA HR** and **JYSK HR** products.

## 1. Apply the patch

```bash
git checkout -b sprint-10.5
git apply sprint-10.5-production-launch-and-catalog-expansion.patch
```

## 2. Environment variables

### Backend (Spring Boot)
| Variable | Required | Default (dev) | Notes |
|---|---|---|---|
| `DATABASE_URL` | prod | `jdbc:postgresql://localhost:5432/budgetspace` | JDBC URL |
| `DATABASE_USERNAME` | prod | `budgetspace` | |
| `DATABASE_PASSWORD` | prod | `budgetspace` | use a secret, never commit |
| `SERVER_PORT` | no | `8080` | |
| `CORS_ALLOWED_ORIGINS` | **prod (required)** | `http://localhost:5173,http://127.0.0.1:5173` | comma-separated frontend origin(s). In the `prod` profile there is no localhost fallback — the app fails fast if it is unset. |
| `SPRING_PROFILES_ACTIVE` | prod | (none) | set to `prod` to lock down admin endpoints + enforce CORS |
| `BUDGETSPACE_REAL_CATALOG_SEED_ENABLED` | no | `true` | seed the real IKEA/JYSK catalog on startup |
| `BUDGETSPACE_ADMIN_ENDPOINTS_ENABLED` | no | `true` dev / `false` prod | dev collector/import/admin endpoints |

### Frontend (Vite)
| Variable | Required | Default | Notes |
|---|---|---|---|
| `VITE_API_BASE_URL` | prod | `http://localhost:8080` | backend base URL, baked in at build time |

## 3. Run the backend

```bash
docker compose up -d db            # PostgreSQL
cd backend
SPRING_PROFILES_ACTIVE=prod \
CORS_ALLOWED_ORIGINS=https://your-frontend.example \
mvn spring-boot:run                # or ./mvnw spring-boot:run if a wrapper is present
```

On startup `RealCatalogSeeder` runs after `data.sql` and logs, e.g.:

```
Real catalog seed: starting (resources=[...]).
Real catalog seed: loaded 71 row(s) from /catalog/real-ikea-jysk-hr-living-room.json.
Real catalog seed: loaded 5 row(s) from /catalog/real-ikea-jysk-hr-living-room-expansion.json.
Real catalog seed: imported real products (received=76, created=76, updated=0, skipped=0).
Real catalog seed: retired N legacy sample living-room product(s).
Real catalog summary: totalProducts=..., IKEA=41, JYSK=35, usableLivingRoom=...
```

## 4. Run the frontend

```bash
cd frontend
VITE_API_BASE_URL=https://your-backend.example npm run build
npm run preview        # or serve dist/ behind your web server
```

## 5. Verify the real catalog is live

```bash
# Living-room products only, with real product URLs (no homepage links):
curl "http://localhost:8080/api/products?roomType=living-room" | grep -o 'https://[^"]*' | sort -u | head
```
You should see `https://www.ikea.com/hr/hr/p/...` and `https://jysk.hr/...` product pages.

Catalog size today (honestly web-verified, not fabricated): **76 products — IKEA 41, JYSK 35**.

## 6. UI smoke test

1. Open the frontend, enter: `Imam 1500 € za dnevni boravak, moderno, već imam TV.`
2. Generate the plan. Each product card shows **name, retailer, category, price (EUR)** and **"Otvori u trgovini"**.
3. Click **"Otvori u trgovini"** → a real IKEA/JYSK product page opens in a new tab
   (`target="_blank"`, `rel="noopener noreferrer"`). If a product has no link the button is disabled
   ("Link proizvoda nije dostupan").
4. To see an IKEA armchair, ask for one explicitly: `... trebam i fotelju.`

## 7. How to know real products are used

- Product URLs are real product pages, not `ikea.com/hr/hr/` or `jysk.hr/` homepages.
- Backend log line per request: `Plan generated: room=living-room, ... retailers=[IKEA+JYSK] ...`.
- Fake `data.sql` sample products have their `living-room` tag retired at startup, so they cannot
  appear in a living-room plan.

## 8. Error states the user may see

| Situation | Message |
|---|---|
| Catalog empty / no match | "Nema dovoljno proizvoda za ovaj zahtjev. Pokušaj povećati budžet ili ukloniti ograničenje trgovine." |
| Backend unreachable | "Backend trenutno nije dostupan. Provjeri je li server pokrenut i pokušaj ponovno." |
| Product has no link | "Link proizvoda nije dostupan" (disabled button) |
| Partial plan | catalog warning from the planner (best available combination) |

## 9. Known limitations (intentionally out of scope)

- **Catalog size:** target was ~200 (IKEA ~100 / JYSK ~100). Only products whose name, price and live
  product URL could be verified are included — no fabricated data. Reaching ~200 needs an official
  IKEA/JYSK product feed or a controlled collector run; listing-page scraping and browser automation
  were intentionally not used. Add more approved snapshots to
  `backend/src/main/resources/catalog/` and register them in `RealCatalogSeeder.SNAPSHOT_RESOURCES`.
- **`ddl-auto: create`:** the schema (and saved plans) is recreated on every backend restart. Fine
  for a pilot; for durable production switch to `validate` + migrations.
- **Images:** snapshot `imageUrl` is empty; the UI uses category fallback images. No fake image URLs
  are shipped.
- **Scope:** living-room only; IKEA HR + JYSK HR only. Other rooms/retailers are unchanged.
