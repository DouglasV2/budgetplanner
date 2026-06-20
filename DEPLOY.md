# Deploy checklist — BudgetSpace AI

A practical, ordered path from "runs on a laptop via docker-compose" to "real users can sign in and pay".
**No secret values live in this file** — only variable names. Keep real values in the host's secret store.

The app is three pieces:

1. **Frontend** — a static Vite/React build (`frontend/`, `npm run build` → `frontend/dist/`). Serve from any
   static host / CDN. It calls the backend at **build-time** `VITE_API_BASE_URL` ([client.ts](frontend/src/api/client.ts):3).
2. **Backend** — a Spring Boot container (`backend/`). Needs a persistent Postgres + the env below.
3. **Database** — managed PostgreSQL (NOT the throwaway compose one).

## Recommended hosting (solo, low-ops, cheap)

- **Railway** or **Render** — easiest: deploy backend container + managed Postgres + static frontend from GitHub,
  HTTPS + env-var UI included (~€5–10/mo). Best if you want to move fast.
- **Fly.io** — cheapest flexible option (~€3–5/mo), great for Docker + Fly Postgres; a bit more config.
- **Hetzner VPS** (€4/mo) — only if you want to self-manage Docker + Postgres + TLS + backups. More ops; not
  recommended pre-traction.

The steps below are provider-agnostic.

## Gate 0 — before ANY public traffic

### 1. Rotate every secret shared during development
The Stripe secret key, Gemini key and eBay **PROD** Cert ID were pasted in chat — treat them as compromised.
Generate fresh ones and put them ONLY in the host's secret store (never in git, never in `VITE_*`):
- Stripe → roll the secret key (and switch to **LIVE** keys, see Gate 1).
- Gemini → regenerate the API key.
- eBay → regenerate the Cert ID (Client Secret).

### 2. Persistent database (this is THE big one)
Today `HIBERNATE_DDL_AUTO` defaults to **`create`** → every backend restart **drops and rebuilds the whole
schema**, wiping accounts, Plus subscriptions and saved plans. That is a dev/pilot setting.
- Point `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` at the **managed** Postgres (real password, not
  the compose `budgetspace/budgetspace`).
- Set **`HIBERNATE_DDL_AUTO=update`** (Hibernate adds new tables/columns, never drops). This is what makes
  accounts, subscriptions **and the AI usage counters** survive restarts.
- Later, for controlled schema changes, add **Flyway** + switch to `validate` (TASKS item (c)).

### 3. HTTPS + the session cookie
- Serve everything over HTTPS (the host usually does this automatically).
- Set **`BUDGETSPACE_AUTH_COOKIE_SECURE=true`** so the `bs_auth` session cookie is `Secure`.

### 4. Point the pieces at the prod domain
- Build the frontend with **`VITE_API_BASE_URL=https://api.yourdomain`** (the backend's public URL).
- Backend **`CORS_ALLOWED_ORIGINS=https://yourdomain`** (the frontend origin; comma-separated if more than one).
- **Google Cloud Console** → OAuth client → add `https://yourdomain` to **Authorized JavaScript origins**
  (and remove `localhost:5180` for prod). Keep `BUDGETSPACE_GOOGLE_CLIENTID` the same (it's public).
- Legal: fill in the **Impressum** placeholders ([legal.ts](frontend/src/legal.ts)) and have the
  Privacy/Terms templates reviewed.

## Gate 1 — Stripe go-live + ops

### 5. Stripe: switch to LIVE
- In Stripe, toggle to **Live mode**; create the live €5.99 product/price; set `STRIPE_SECRET_KEY` (live),
  `STRIPE_PUBLISHABLE_KEY` (live), `STRIPE_PLUS_PRICE_ID` (live).
- Confirm the live checkout `success_url`/`cancel_url` resolve to the prod domain (built from the request Origin —
  ensure CORS/Origin is the prod domain).

### 6. Stripe webhook (the production billing path)
The code is ready and **dormant until the secret is set** ([BillingService](backend/src/main/java/ai/budgetspace/billing/BillingService.java)).
- Stripe Dashboard → Developers → Webhooks → **add endpoint** `https://api.yourdomain/api/billing/webhook`,
  events: `checkout.session.completed` + `customer.subscription.deleted`.
- Copy the signing secret into **`STRIPE_WEBHOOK_SECRET`**. The webhook then becomes the source of truth
  (upgrade on completed, downgrade on cancel); account-deletion already cancels the subscription (10.73).

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
- Add **error monitoring** (e.g. Sentry) — alerts on 500s, the AI budget breach, webhook failures. You can't watch
  logs by hand.
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
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Managed Postgres | password = secret |
| `HIBERNATE_DDL_AUTO` | `update` in prod (NOT `create`) | no |
| `BUDGETSPACE_AUTH_COOKIE_SECURE` | `true` in prod | no |
| `CORS_ALLOWED_ORIGINS` | prod frontend origin(s) | no |
| `VITE_API_BASE_URL` (frontend build) | backend public URL | no |
| `BUDGETSPACE_GOOGLE_CLIENTID` | Google OAuth client id | no (public) |
| `STRIPE_SECRET_KEY` | Stripe live secret | **yes** |
| `STRIPE_PUBLISHABLE_KEY` | Stripe live publishable | no (public) |
| `STRIPE_PLUS_PRICE_ID` | Live €5.99 price id | no |
| `STRIPE_WEBHOOK_SECRET` | Webhook signing secret | **yes** |
| `GEMINI_API_KEY` | Gemini key (rotated) | **yes** |
| `BUDGETSPACE_AI_MONTHLY_BUDGET_USD` | AI wallet hard stop | no |
| `BUDGETSPACE_MARKETPLACEFEEDS_EBAY_CLIENTID` / `...CLIENTSECRET` | eBay PROD keys (rotated) | secret = the secret |
