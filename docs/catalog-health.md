# Catalog health (dev-only)

Sprint 9.6. Dev alat koji odgovara na pitanje: **ima li katalog dovoljno dobrih proizvoda da
planner složi koristan plan?** Nije korisnički ekran.

## Endpoint

```bash
curl http://localhost:8080/api/products/catalog-health
```

## Što "usable" znači

Proizvod je *usable* ako ga planner smije koristiti (`canEnterPlanner`): na zalihi, ima
pozitivnu cijenu, nije `unavailable`, nije `needs-review`, ima `roomTags` i `styleTags`.
Sve ostalo je *blocked*. Sprint 10.3 dodatno zaključava pravilo da proizvod bez cijene ili
s cijenom `0` ne smije ući u planner ni kada je označen kao `in-stock`.

## Response

```json
{
  "totalProducts": 52,
  "usableProducts": 40,
  "blockedProducts": 12,
  "unavailableProducts": 3,
  "needsReviewProducts": 5,
  "staleProducts": 4,
  "missingUrlProducts": 2,
  "missingImageProducts": 6,
  "byRoom": { "living-room": 18, "bedroom": 10, "home-office": 9, "home-gym": 3 },
  "byCategory": { "sofa": 4, "tv-unit": 3, "table": 2, "rug": 3 },
  "byRetailer": { "IKEA": 22, "JYSK": 10, "Lesnina": 8 },
  "byDataQuality": { "complete": 28, "partial": 12, "needs-review": 5, "unknown": 7 },
  "plannerReadiness": [
    { "room": "living-room", "ready": true, "missingRequiredCategories": [], "weakCategories": ["rug"], "usableProducts": 18 },
    { "room": "bedroom", "ready": false, "missingRequiredCategories": ["mattress"], "weakCategories": ["rug", "lighting"], "usableProducts": 6 }
  ]
}
```

## Pravila spremnosti (po sobi)

Centralizirano u `PlannerReadiness` (isti izvor koristi i planner za „djelomičan plan”):

| Soba | Required (bez njih je plan djelomičan) | Recommended (ako tanko → weak) |
| --- | --- | --- |
| living-room | sofa, tv-unit | table, rug, lighting |
| bedroom | bed, mattress | storage, lighting, rug |
| home-office | desk, chair | lighting, storage |
| home-gym | gym-equipment | storage, lighting |

- `ready` je `true` kad svaka **required** kategorija ima barem jedan usable proizvod.
- `missingRequiredCategories` su required kategorije bez ijednog usable proizvoda.
- `weakCategories` su recommended kategorije s manje od 2 usable proizvoda.

## Veza s plannerom

Kad korisnik traži sobu kojoj fali required kategorija, plan response ima `partialPlan: true`
i `catalogWarning` (ljudski tekst). Planner **ne** izmišlja proizvode da popuni rupu —
prikaže najbolju dostupnu kombinaciju i jasno kaže da je djelomična. U korisničkom UI-u se
ne koriste riječi „catalog”, „health”, „coverage”, „usable”.

## Sprint 10.3 production-pilot gate

Za IKEA + JYSK living-room pilot postoji širi offline snapshot i scenario matrix:

- `docs/catalog-snapshots/real-ikea-jysk-hr-living-room-production-snapshot.json`
- `docs/production-pilot/living-room-scenario-matrix.json`

Minimalni production-pilot prag za `living-room` nije samo `ready: true`, nego i dovoljan
izbor po kategorijama: sofa/TV unit/table/rug/lighting barem 8 usable proizvoda, storage
barem 6, decor barem 4 i chair barem 3. To testira
`LivingRoomProductionScenarioMatrixTest` bez live interneta.
