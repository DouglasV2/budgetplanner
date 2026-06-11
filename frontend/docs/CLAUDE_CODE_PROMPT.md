You are helping me continue a React + Vite + TypeScript MVP called BudgetSpace AI.

BudgetSpace AI is a prompt-first AI shopping planner for furnishing rooms within a budget. Users enter a natural-language prompt such as:

"I have 1500 € for a 20 m² living room in Zagreb. I want Scandinavian style. Use IKEA and JYSK if possible. I already have a TV, but I need a sofa, rug, lamp and TV unit."

The current project is frontend-only and uses a mock product catalog.

## Current stack

- React
- Vite
- TypeScript
- CSS only, no Tailwind yet
- Local recommendation engine in `src/utils/planner.ts`
- Mock product data in `src/data/products.ts`

## Current features

- Landing page
- Prompt-first planner UX
- Budget and room-size controls
- Room type and style selection
- Retailer filters:
  - single retailer mode
  - multi-retailer mode
  - IKEA, JYSK, Pevex, Emmezeta, Decathlon
- Optimization goals:
  - best value
  - lowest price
  - least stores
  - style match
- Must-have categories
- Already-have categories
- Generated plans:
  - Budget plan
  - Best value plan
  - Stretch plan
- Fit score
- Style match score
- Shopping effort score
- Replace product button
- Copy/share plan button

## What I want next

Help me evolve this MVP without overengineering.

Priority order:

1. Improve the recommendation engine so it handles missing products and fallback alternatives more gracefully.
2. Add a nicer result comparison view.
3. Add a saved-plan UI using localStorage first.
4. Add fake product detail drawer/modal.
5. Prepare an API contract for a Spring Boot backend.
6. Later add real database + scraper architecture.

## Important product direction

This should not feel like a generic form app. It should feel like:

"Tell us what kind of room you want, and we build a realistic shopping plan from local retailers."

The prompt should remain the main interaction. Filters are power-user controls.

Keep the UI warm, human, premium, and simple.
