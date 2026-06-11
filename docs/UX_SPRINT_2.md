# UX Sprint 2 — Prompt-first planner

This version moves the product closer to a real user flow:

- Prompt is the primary input.
- Budget is a direct number input instead of a slider.
- Room size uses human presets: unknown, small, medium, large, plus custom m².
- Starter templates help users who do not know what to write.
- Results start with a "Razumjeli smo" summary so users see what the app understood.
- Plans include quick actions: make cheaper, make nicer, one store only, fewer stores.
- Users can lock products they like before regenerating.
- Backend honors `lockedProductIds` during generation.
- Each plan shows retailer spend breakdown.
- Each plan highlights missing must-have categories when budget/catalog cannot satisfy them.

Recommended next step:

1. Replace seed products with 50–100 real manually curated products.
2. Add click tracking for product links.
3. Add a simple feedback button: useful / not useful.
4. Only then connect Claude/OpenAI for better explanations and prompt parsing.
