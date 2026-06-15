# Data model

## Product

The first MVP keeps the catalog intentionally simple.

```text
id              string primary key
name            string
retailer        string: IKEA, JYSK, Pevex, Emmezeta, Decathlon, Lesnina
category        string: sofa, tv-unit, table, rug, lighting, storage, decor, desk, chair, bed, mattress, gym-equipment
price           decimal
originalPrice   decimal nullable
styleTags       comma-separated string
roomTags        comma-separated string
image           URL
url             product URL
rating          number
inStock         boolean
note            short recommendation note
reviewCount     number nullable   (Sprint 10.13 #2 — aggregate shown only when present)
reviewsUrl      URL nullable      (Sprint 10.13 #2 — falls back to product URL)
market          string nullable   (Sprint 10.13 #3 — e.g. HR/SI/AT/DE; null = global, visible on all markets)
```

> Note: the model has grown since the MVP. Other live fields include `colorTags`, `materialTags`,
> `availabilityStatus`, `deliveryNote`, `lastCheckedAt`, `priceTier`, and affiliate groundwork
> (`originalProductUrl`, `affiliateUrl`, `sponsored`, `sponsorLabel`).

Later, split into proper relational tables:

- retailers
- products
- product_prices
- product_images
- product_availability
- product_tags
- plans
- plan_items
- user_projects

## Planner input

```text
prompt
budget
roomType
style
location
size
retailerMode
selectedRetailers
optimizationGoal
mustHaveCategories
alreadyHaveCategories
market                (Sprint 10.13 #3 — defaults to HR; filters catalog + drives currency)
```

## Planner output

```text
plans[]
  id
  name
  label
  description
  items[]
  total
  savings
  fitScore
  shoppingEffort
  styleConsistency
  retailersUsed[]
```
