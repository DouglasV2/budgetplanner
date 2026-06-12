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
```

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
