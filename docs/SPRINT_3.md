# Sprint 3 — user-friendly validation MVP

This sprint moves the prototype closer to a real validation product.

## Added

- Save a generated plan to the backend.
- Open saved plans through `/plan/{id}`.
- Copy a shareable plan link.
- Copy a plain-text shopping list.
- Track clicks on product links.
- Collect simple plan feedback:
  - useful
  - too expensive
  - wrong style
  - too many stores
- Replace more technical UI copy with user-facing Croatian language.

## New backend endpoints

```http
POST /api/saved-plans
GET /api/saved-plans/{id}
POST /api/events/product-click
POST /api/events/plan-feedback
```

## Why this matters

The app can now answer the most important MVP questions:

- Do users save plans?
- Do users share plans?
- Which products do users click?
- Why do users reject a plan?

These signals are more important than adding many more filters at this stage.
