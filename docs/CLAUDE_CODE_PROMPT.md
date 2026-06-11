You are working on BudgetSpace AI, a full-stack MVP for an AI-powered room furnishing planner.

Current stack:
- frontend: React + Vite + TypeScript
- backend: Spring Boot + JPA + PostgreSQL
- product data: seeded in backend/src/main/resources/data.sql
- planner: deterministic recommendation logic in PlannerService

Goal for the next coding session:
Make the product feel closer to a real startup MVP without over-engineering.

Tasks:
1. Run the project locally and fix any compile/runtime issues.
2. Improve frontend polish while keeping the current UX direction.
3. Add plan persistence:
   - RoomPlan entity
   - PlanItem entity
   - POST /api/plans/save
   - GET /api/plans/{id}
4. Add shareable plan URLs in frontend.
5. Add outbound product click tracking endpoint.
6. Keep the LLM disabled for now. Do not allow AI to invent products.
7. Add tests for PlannerService core selection logic.
8. Add README updates for every new command or env var.

Important product principle:
The user should feel that BudgetSpace AI saves them from manually browsing IKEA, JYSK, Pevex, Emmezeta and Decathlon for hours.
