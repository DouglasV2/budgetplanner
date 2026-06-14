# Living room production pilot — Sprint 10.3

Sprint 10.3 moves the real-retailer work from “URL collector smoke test” toward a
production-like living room pilot. Scope is intentionally narrow:

- room: `living-room`
- retailers: IKEA HR + JYSK HR
- styles: `modern`, `minimal`, `cozy`, `classic`, `industrial`, `boho`
- budget range: roughly 600–3000 EUR
- user intents: retailer-only, retailer exclusion, preferred retailer, max-one-store,
  already-owned categories, locked product, must-have category

This is not a mass crawler and not a final production feed. The snapshot is a curated QA/dev
fixture with real retailer-hosted product URLs. Before a public release, run the collector or an
official feed again to refresh price, image and stock data.

## Files

| File | Purpose |
|---|---|
| `docs/catalog-snapshots/real-ikea-jysk-hr-living-room-production-snapshot.json` | 70+ curated IKEA/JYSK living-room products for offline production-pilot planning tests. |
| `docs/production-pilot/living-room-scenario-matrix.json` | 32 realistic user scenarios used as a golden scenario matrix. |
| `backend/src/test/java/ai/budgetspace/planner/LivingRoomProductionScenarioMatrixTest.java` | Offline test that loads the snapshot, runs the matrix through `PlannerService`, and checks safety/intent rules. |

## What the matrix covers

The matrix does not attempt every product combination. It covers the important classes of
real user behavior:

- IKEA-only and JYSK-only plans
- IKEA + JYSK mixed plans
- “prefer IKEA” and “prefer JYSK”
- “no JYSK” and “no IKEA” exclusions
- low, normal and higher budgets
- basic, comfort and complete furnishing levels
- already-owned TV unit, rug, sofa, lighting, storage or decor
- must-have decor / chair
- locked product retained across generated plans
- guardrails for products without positive price, unavailable products and `needs-review`

## Quality gates

Before using this as a production pilot seed, the following must hold:

1. The snapshot has enough usable living-room coverage:
   - sofa: at least 8
   - TV unit: at least 8
   - table: at least 8
   - rug: at least 8
   - lighting: at least 8
   - storage: at least 6
   - decor: at least 4
   - chair: at least 3
2. Every planner item has:
   - positive price
   - real `ikea.com` or `jysk.hr` product URL
   - supported retailer/category/style/room taxonomy
   - `availabilityStatus != unavailable`
   - `dataQuality != needs-review`
3. Retailer intent is respected:
   - single-retailer scenarios stay single retailer
   - excluded retailers do not appear in output
   - already-owned categories are not recommended again
4. Partial-plan behavior is explicit. The production-pilot matrix expects complete plans for
   its chosen living-room scenarios; insufficient catalog tests should be kept separate.

## How to run

From project root:

```bash
cd backend
mvn test -Dtest=LivingRoomProductionScenarioMatrixTest,ProductTaxonomyTest
```

If you are running from a clean machine, first make sure the backend dependencies are installed
and the local Maven command is available. This test is offline and does not call IKEA/JYSK.

## Production note

The production-ready flow should eventually be:

1. collect or import approved IKEA/JYSK source data,
2. review `needs-review` rows,
3. reject rows without positive price,
4. run catalog health,
5. run the scenario matrix,
6. deploy only if all gates pass.

Do not expose collector/discovery endpoints as normal user-facing production features.
