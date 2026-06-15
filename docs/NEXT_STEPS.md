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

## Sprint 10.10 (done): AI intent extraction + monetization guardrails

Shipped:

- LLM provider abstraction (`LlmProvider`, `LlmClient`, `OpenAiLlmClient`, `AnthropicLlmClient`,
  `LlmClientFactory`) — provider-agnostic, off by default, keys backend-only.
- `PromptIntelligenceService` → `PlannerIntentAnalysisDto` (strict JSON), mapped to `PlannerInputDto`;
  the deterministic planner still picks real products (LLM never invents products/prices/URLs).
- Rule-based fallback on every failure path (disabled / no key / malformed / limit hit).
- `AiUsageTracker` — in-memory usage + cost/rate guardrails (monthly USD, per-day, per-session).
- Affiliate/sponsored groundwork on the product model (`originalProductUrl`, `affiliateUrl`,
  `sponsored`, `sponsorLabel`) — no aggressive ad UI.
- Value-first pricing UI (Free / Pro / Pro+), AI-insight + low-confidence display.

### What's next

1. **Bathroom catalog** (deferred from 10.7/10.9): verify 2-3 real JYSK bathroom storage products
   with prices and add to a snapshot, or hide bathroom from the room dropdown until ready.
2. **Wire AI in a staging env**: set `BUDGETSPACE_AI_ENABLED=true`, `BUDGETSPACE_LLM_PROVIDER=openai`
   and a real `OPENAI_API_KEY`; verify intent extraction quality on messy Croatian prompts.
3. **Persist usage**: move `AiUsageTracker` to a DB table (`ai_usage_event`) when multi-instance.
4. **Stripe**: only after pricing is validated; keep the value-first, context-aware upgrade approach.
5. **Affiliate population**: fill `affiliateUrl`/`originalProductUrl` from a partner program; keep
   sponsored clearly labelled and never replacing the best organic pick.
6. **Structured outputs**: consider OpenAI `json_schema` strict mode / official SDKs once stable.
