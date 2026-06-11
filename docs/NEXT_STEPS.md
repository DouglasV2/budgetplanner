# Next steps

## Sprint 2: make it feel real

1. Add saved plans table.
2. Add shareable public plan URL.
3. Add product detail modal.
4. Add analytics events:
   - prompt submitted
   - plan generated
   - product clicked
   - product replaced
   - plan copied
5. Add affiliate-ready outbound links with `utm_source=budgetspace`.

## Sprint 3: LLM layer

Do not let the LLM invent products. Let backend retrieve candidates first, then pass only those candidates to the LLM.

Flow:

```text
Prompt
  -> parse intent
  -> query products
  -> deterministic candidate shortlist
  -> LLM ranks/explains plan
  -> backend validates total price and product IDs
```

## Sprint 4: scraping

Start with one retailer and one category.

Suggested first vertical:

```text
IKEA + JYSK living room products
```

Store raw scrape payloads separately so bugs do not corrupt normalized product data.
