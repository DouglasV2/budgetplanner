# API Draft

## Generate plans

```http
POST /api/plans/generate
Content-Type: application/json
```

Request:

```json
{
  "prompt": "Imam 1500 € za dnevni boravak, samo IKEA, već imam TV.",
  "budget": 1500,
  "roomType": "living-room",
  "style": "scandinavian",
  "location": "Zagreb",
  "size": 20,
  "retailerMode": "single",
  "selectedRetailers": ["IKEA"],
  "optimizationGoal": "best-value",
  "mustHaveCategories": ["sofa", "rug", "lighting"],
  "alreadyHaveCategories": ["tv-unit"]
}
```

Response:

```json
{
  "input": {},
  "plans": [
    {
      "id": "budget",
      "name": "Budget plan",
      "total": 1180,
      "items": []
    }
  ]
}
```

The backend returns a normalized `input` because it parses the prompt and may update budget, room, style, retailers, must-have and already-have categories.

## Replace product

```http
POST /api/plans/replace
Content-Type: application/json
```

Request:

```json
{
  "plan": {},
  "input": {},
  "productId": "ikea-sofa-01"
}
```

Response is the updated plan.

## Product search

```http
GET /api/products?retailer=IKEA&category=sofa&roomType=living-room&maxPrice=800
```
