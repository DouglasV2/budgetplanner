# Deploy checklist — BudgetSpace AI

A practical, ordered path from "runs on a laptop via docker-compose" to "real users can sign in and pay".
**No secret values live in this file** — only variable names. Keep real values in the host's secret store.

The app is three pieces:

1. **Frontend** — a static Vite/React build (`frontend/`, `npm run build` → `frontend/dist/`). Serve from any
   static host / CDN, or use **`frontend/Dockerfile`** (builds the bundle → nginx). It calls the backend at
   **build-time** `VITE_API_BASE_URL` ([client.ts](frontend/src/api/client.ts):3).
2. **Backend** — a Spring Boot container built from **`backend/Dockerfile`** (multi-stage → slim JRE + fat jar,
   with `SPRING_PROFILES_ACTIVE=prod` baked in). Needs a persistent Postgres + the env below.
3. **Database** — managed PostgreSQL (NOT the throwaway compose one).

**Build & run.** `backend/Dockerfile` + `frontend/Dockerfile` are the production artifacts (and `backend/mvnw`
builds the jar without a host Maven). For a single box, **`docker-compose.prod.yml`** wires both images + Postgres
with the prod profile, `restart: unless-stopped`, and a DB healthcheck —
`docker compose -f docker-compose.prod.yml up -d --build`. On Railway/Render/Fly, point each service at its
Dockerfile. The dev `docker-compose.override.yml` (`mvn spring-boot:run` + the Vite dev server) is NOT for prod.
The backend image ships a `HEALTHCHECK` (curl `/actuator/health`) — point the host's health probe there too
(`/actuator/health/readiness` for readiness gating). A per-IP rate-limit backstop guards `/api/plans/*` against
floods (in-memory, per instance — pair it with the host/CDN limiter at real scale). The DB pool size is
`DB_POOL_MAX_SIZE` (default 15; raise it on a managed Postgres that allows more connections).

## Recommended hosting (solo, low-ops, cheap)

- **Railway** or **Render** — easiest: deploy backend container + managed Postgres + static frontend from GitHub,
  HTTPS + env-var UI included (~€5–10/mo). Best if you want to move fast.
- **Fly.io** — cheapest flexible option (~€3–5/mo), great for Docker + Fly Postgres; a bit more config.
- **Hetzner VPS** (€4/mo) — only if you want to self-manage Docker + Postgres + TLS + backups. More ops; not
  recommended pre-traction.

The steps below are provider-agnostic.

## Gate 0 — before ANY public traffic

### 0. Activate the prod profile (the one switch that makes the rest safe)
Set **`SPRING_PROFILES_ACTIVE=prod`** — `backend/Dockerfile` already bakes it in; set it explicitly if you run the
jar another way. This single variable flips every safe default: non-destructive schema (`ddl-auto=validate`),
admin/collector endpoints OFF, the session cookie `Secure`, and a required explicit CORS origin. **Without it the
base (dev) profile wins** — `ddl-auto=create` (drops & rebuilds the whole schema on every restart, wiping accounts
+ subscriptions) and PUBLIC admin/catalog-mutation endpoints. As belt-and-suspenders also set
**`BUDGETSPACE_ADMIN_ENDPOINTS_ENABLED=false`**.

### 0b. Right-size the instance + JVM heap (memory)
Pick a backend instance with **≥512 MB RAM (1 GB comfortable)** and set **`JAVA_OPTS=-XX:MaxRAMPercentage=75.0`**
(already the default in `docker-compose.prod.yml`; on Railway/Fly set it as a service env var). The JVM otherwise
may not size its heap to the container limit and can OOM under load. **The catalog size is NOT the constraint** —
~2.4k products is only a few MB in memory; the JVM baseline (Spring Boot + Hibernate, ~250–400 MB) is what sizes
the instance, so adding catalog depth doesn't change this. `MaxRAMPercentage=75` leaves ~25% for metaspace/threads
(don't go to 100). Catalog filtering is in-memory + O(N) but trivial at this size; revisit only past ~10k products.

### 1. Rotate every secret shared during development
The Stripe secret key, Gemini key and eBay **PROD** Cert ID were pasted in chat — treat them as compromised.
Generate fresh ones and put them ONLY in the host's secret store (never in git, never in `VITE_*`):
- Stripe → roll the secret key (and switch to **LIVE** keys, see Gate 1).
- Gemini → regenerate the API key.
- eBay → regenerate the Cert ID (Client Secret).

### 2. Persistent database (this is THE big one)
The **base** profile defaults `HIBERNATE_DDL_AUTO=create` (a dev setting — rebuilds the schema on every restart).
The **prod profile** (step 0) instead lets **Flyway** own the schema (`backend/.../db/migration/V1__baseline.sql`)
with `ddl-auto=validate` checking it. So in prod:
- Point `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` at the **managed** Postgres (real password, not
  the compose `budgetspace/budgetspace`).
- Leave **`HIBERNATE_DDL_AUTO=validate`** (the prod default). Flyway creates the schema on a fresh DB and applies
  every future migration; `validate` just confirms it matches the entities. **Never** set `create`/`update` in
  prod — ship a new `V2__*.sql` migration instead. An already-`update`-bootstrapped DB is adopted automatically
  (`baseline-on-migrate` marks the existing schema as V1).

### 3. HTTPS + the session cookie
- Serve everything over HTTPS (the host usually does this automatically).
- Set **`BUDGETSPACE_AUTH_COOKIE_SECURE=true`** so the `bs_auth` session cookie is `Secure`.
- **Cookie topology — read this before you pick where things live.** The `bs_auth` session cookie is
  `SameSite=Lax` by default, which means **sign-in and Stripe checkout only work if the frontend and backend share
  a registrable domain** (e.g. `yourdomain` + `api.yourdomain`). This is the recommended layout — pick it and you
  do nothing here. **The trap:** a split-host setup (e.g. frontend on `*.vercel.app`, backend on `*.railway.app` —
  unrelated domains) silently breaks auth, because a `Lax` cookie won't ride cross-site XHR. It works locally and
  fails only in prod. If you must split hosts, set **`BUDGETSPACE_AUTH_COOKIE_SAMESITE=None`** (HTTPS required;
  the app forces `Secure` on automatically for `None`).

### 4. Point the pieces at the prod domain
- Build the frontend with **`VITE_API_BASE_URL=https://api.yourdomain`** (the backend's public URL).
- Backend **`CORS_ALLOWED_ORIGINS=https://yourdomain`** (the frontend origin; comma-separated if more than one).
- **Google Cloud Console** → OAuth client → add `https://yourdomain` to **Authorized JavaScript origins**
  (and remove `localhost:5180` for prod). Keep `BUDGETSPACE_GOOGLE_CLIENTID` the same (it's public).
- Legal: fill in the **Impressum** placeholders ([legal.ts](frontend/src/legal.ts)) and have the
  Privacy/Terms templates reviewed.

## Gate 1 — Stripe go-live + ops

### 5. Stripe: switch to LIVE
- **First flip `BUDGETSPACE_BETA_MODE=false`.** While beta-mode is true the billing endpoints are refused
  server-side (`BillingController` → 503 / webhook no-op), so setting Stripe keys alone does NOT enable
  charging — this is the fail-safe that prevents a charge firing while the UI still says "free beta".
  Do NOT go live on billing until the Terms/checkout model is reconciled (one-time vs subscription) and the
  pre-charge items in [legal-launch-checklist.md](docs/legal-launch-checklist.md) §C are done.
- In Stripe, toggle to **Live mode**; create the live €5.99 product/price; set `STRIPE_SECRET_KEY` (live),
  `STRIPE_PUBLISHABLE_KEY` (live), `STRIPE_PLUS_PRICE_ID` (live).
- Confirm the live checkout `success_url`/`cancel_url` resolve to the prod domain (built from the request Origin —
  ensure CORS/Origin is the prod domain).

### 6. Stripe webhook (the production billing path)
The code is ready and **dormant until the secret is set** ([BillingService](backend/src/main/java/ai/budgetspace/billing/BillingService.java)).
- Stripe Dashboard → Developers → Webhooks → **add endpoint** `https://api.yourdomain/api/billing/webhook`,
  events (subscribe to **all four** — the handler acts on each):
  - `checkout.session.completed` — grants Plus on first payment.
  - `customer.subscription.created` + `customer.subscription.updated` — drive Plus off the live subscription
    status. **`updated` is the dunning path**: a failed card flips the sub to `past_due`/`unpaid` and the handler
    downgrades to Free. **Omit `updated` and a non-paying customer keeps Plus for free** — the 10.84 dunning code
    only ever runs if Stripe is sending you this event.
  - `customer.subscription.deleted` — revokes Plus on cancellation.
- Copy the signing secret into **`STRIPE_WEBHOOK_SECRET`**. The webhook then becomes the source of truth
  (status-driven grant/revoke); account-deletion already cancels the subscription (10.73). Duplicate deliveries are
  idempotent (the event id is recorded once it's applied).

### 7. AI budget (already capped — just set the numbers)
- `BUDGETSPACE_AI_ENABLED=true`, `BUDGETSPACE_LLM_PROVIDER=gemini`, `GEMINI_API_KEY=<rotated>`.
- Wallet hard stop: `BUDGETSPACE_AI_MONTHLY_BUDGET_USD` (start small, e.g. 5–10).
- Per-tier daily caps (optional overrides): `BUDGETSPACE_AI_DAILY_GUEST/FREE/PLUS/PRO`.
- AI parsing verified live across all 15 markets (HR/SI/AT/DE/IT/FI/FR/NL/SK/ES/PT/NO/SE/DK/GB → Gemini).

### 8. eBay "Rabljeno"
- `BUDGETSPACE_MARKETPLACEFEEDS_EBAY_CLIENTID` + `BUDGETSPACE_MARKETPLACEFEEDS_EBAY_CLIENTSECRET` (rotated PROD).
- Guardrail stays: eBay data is request-time, in-memory only — never persisted.

### 9. Backups + monitoring
- Enable **automated Postgres backups** on the managed DB (and test a restore once).
- **Error monitoring is wired (Sentry, Sprint 10.96)** — just turn it on: create a Sentry project, copy its DSN
  (Settings → Client Keys) into **`SENTRY_DSN`**, and set **`SENTRY_ENVIRONMENT=production`**. Every ERROR-level log
  (the catch-all 500s + Stripe failure paths) is then sent to Sentry with its stack trace, so you're alerted
  instead of reading stdout by hand. Blank `SENTRY_DSN` ⇒ Sentry stays disabled (no-op). To confirm it works after
  deploy, set `SENTRY_DEBUG=true` once and watch the send traces in the logs, then unset it.
- Optional: a privacy-friendly analytics (Plausible) for the Free→Plus funnel.

## Post-deploy smoke test
1. `GET https://api.yourdomain/api/auth/me` → `googleEnabled:true`, `billingEnabled:true`.
2. Sign in with Google on the prod domain → returns your profile.
3. Generate a plan → `intentAnalysis.aiUsed:true`, `source:gemini`.
4. Upgrade to Plus with a **real** card (live mode) → returns as PLUS; confirm the webhook fired in the Stripe
   dashboard.
5. Delete the account (footer) → 204; confirm the row is gone and the Stripe subscription shows cancelled.

## Env var reference

| Variable | What | Secret? |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | **`prod`** — load-bearing; baked into the backend image | no |
| `JAVA_OPTS` | JVM flags; default `-XX:MaxRAMPercentage=75.0` — cap heap so a small instance doesn't OOM | no |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Managed Postgres | password = secret |
| `HIBERNATE_DDL_AUTO` | `validate` (Flyway owns the schema; never `create`/`update` in prod) | no |
| `BUDGETSPACE_ADMIN_ENDPOINTS_ENABLED` | `false` in prod (the profile also forces this) | no |
| `BUDGETSPACE_AUTH_COOKIE_SECURE` | `true` in prod | no |
| `BUDGETSPACE_AUTH_COOKIE_SAMESITE` | `Lax` (same-domain, default) or `None` (split-host; forces Secure) | no |
| `CORS_ALLOWED_ORIGINS` | prod frontend origin(s) | no |
| `VITE_API_BASE_URL` (frontend build) | backend public URL | no |
| `BUDGETSPACE_GOOGLE_CLIENTID` | Google OAuth client id | no (public) |
| `STRIPE_SECRET_KEY` | Stripe live secret | **yes** |
| `STRIPE_PUBLISHABLE_KEY` | Stripe live publishable | no (public) |
| `STRIPE_PLUS_PRICE_ID` | Live €5.99 price id | no |
| `STRIPE_WEBHOOK_SECRET` | Webhook signing secret | **yes** |
| `GEMINI_API_KEY` | Gemini key (rotated) | **yes** |
| `SENTRY_DSN` | Sentry ingest DSN — error alerting; blank = disabled | no (write-only ingest key) |
| `SENTRY_ENVIRONMENT` | tags Sentry events (`production`) | no |
| `BUDGETSPACE_AI_MONTHLY_BUDGET_USD` | AI wallet hard stop | no |
| `BUDGETSPACE_MARKETPLACEFEEDS_EBAY_CLIENTID` / `...CLIENTSECRET` | eBay PROD keys (rotated) | secret = the secret |
