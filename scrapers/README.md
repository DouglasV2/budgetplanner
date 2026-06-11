# Scrapers / Retailer Connectors

This folder is intentionally a skeleton. The MVP uses seeded product data in `backend/src/main/resources/data.sql`.

Next phase:

1. Build one connector at a time.
2. Store normalized products in PostgreSQL.
3. Keep a `source_url`, `last_seen_at`, `price`, `original_price`, and `availability` field.
4. Add rate limiting, robots/ToS review, retries, and monitoring.

Recommended first connector order:

1. IKEA
2. JYSK
3. Pevex
4. Emmezeta
5. Decathlon

Run example:

```bash
python main.py --retailer ikea --limit 50
```
