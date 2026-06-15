# BudgetSpace AI — Full-stack MVP

AI-powered furnishing planner for users who want to equip a room inside a budget using retailers such as IKEA, JYSK, Pevex, Emmezeta and Decathlon.

The planner core is deterministic; an optional AI layer (Sprint 10.10) understands the user's
free-text prompt before planning. AI is **off by default** — with it off the app is fully
deterministic and makes no external calls. See "AI / LLM configuration" below.

- React + Vite + TypeScript frontend
- Spring Boot REST API
- PostgreSQL product catalog
- Seeded catalog with real IKEA/JYSK HR products
- Prompt-first planner flow
- Optional AI prompt understanding (OpenAI or Anthropic), rule-based fallback
- Retailer filters
- Must-have categories
- Already-have categories
- Replace product action
- Share/copy plan action

## AI / LLM configuration

**AI keys are backend-only. Never commit API keys. Never expose them in React.** The frontend bundle
is public — anything in a `VITE_*` variable ships to the browser, so LLM keys live only in backend
environment variables (`OPENAI_API_KEY` / `ANTHROPIC_API_KEY`), are read in `LlmProperties`, and are
never logged or returned in any response. Copy `backend/.env.example` and set real values in your
secret store (real `.env` files are git-ignored).

The AI layer is a **parser/reasoning layer**: it turns a free-text prompt into a structured
`PlannerIntentAnalysisDto`; the deterministic planner still selects the real catalog products (the
LLM never invents products, prices or URLs). It is **off by default** and falls back to the
rule-based parser when disabled, when no key is set, on any error, or when usage limits are hit.

| Variable | Default | Purpose |
|---|---|---|
| `BUDGETSPACE_AI_ENABLED` | `false` | master switch for the AI layer |
| `BUDGETSPACE_LLM_PROVIDER` | `off` | `off` \| `openai` \| `anthropic` |
| `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` | (none) | backend-only provider keys |
| `BUDGETSPACE_LLM_MODEL` | cheap mini per provider | model override |
| `BUDGETSPACE_LLM_TIMEOUT_SECONDS` | `15` | per-call timeout |
| `BUDGETSPACE_LLM_MAX_OUTPUT_TOKENS` | `700` | output cap |
| `BUDGETSPACE_AI_MONTHLY_BUDGET_USD` | `20` | monthly cost guardrail → fallback when exceeded |
| `BUDGETSPACE_AI_MAX_REQUESTS_PER_DAY` | `100` | daily request guardrail |
| `BUDGETSPACE_AI_MAX_REQUESTS_PER_SESSION` | `10` | per-session guardrail |

## Project structure

```text
budgetspace-ai-fullstack-mvp/
  frontend/      React/Vite app
  backend/       Spring Boot API + JPA + PostgreSQL
  scrapers/      Python scraper/connector skeleton
  docs/          API, data model and Claude Code handoff
```

## Run locally

### 1. Start PostgreSQL

```bash
docker compose up -d postgres
```

Database defaults:

```text
DB: budgetspace
User: budgetspace
Password: budgetspace
Port: 5432
```

### 2. Start backend

```bash
cd backend
mvn spring-boot:run
```

Backend runs at:

```text
http://localhost:8080
```

On startup it recreates the product table and loads `src/main/resources/data.sql`.

### 3. Start frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at:

```text
http://localhost:5173
```

Create `frontend/.env` if you need a custom backend URL:

```bash
cp frontend/.env.example frontend/.env
```

## Main endpoints

```http
GET /api/products
POST /api/plans/generate
POST /api/plans/replace
```

Example prompt:

```text
Imam 1500 € za dnevni boravak od 20 m² u Zagrebu. Želim skandinavski stil, samo IKEA. Već imam TV, trebam kauč, tepih, lampu i TV komodu.
```

## What is intentionally not included yet

- Real retailer scraping
- LLM calls
- User accounts
- Saved plans persistence
- Affiliate tracking
- Production deployment

Those are the next phases after this foundation works locally.


## UX Sprint 2 update

This zip includes the prompt-first UX revision:

- no budget slider as the main control; budget is now a precise number input
- room size uses human presets instead of a slider
- starter templates for common use cases
- "Razumjeli smo" summary before generated plans
- quick result actions: cheaper, nicer, one store only, fewer stores
- lock product / unlock product flow
- retailer cost breakdown per plan
- backend support for `lockedProductIds`

See `docs/UX_SPRINT_2.md` for details.
"# budgetplanner" 
