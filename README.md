# BudgetSpace AI — Full-stack MVP

AI-powered furnishing planner for users who want to equip a room inside a budget using retailers such as IKEA, JYSK, Pevex, Emmezeta and Decathlon.

The current MVP is deterministic, not LLM-based yet:

- React + Vite + TypeScript frontend
- Spring Boot REST API
- PostgreSQL product catalog
- Seeded catalog with 150 products
- Prompt-first planner flow
- Retailer filters
- Must-have categories
- Already-have categories
- Replace product action
- Share/copy plan action

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
