-- Sprint 10.188: retire the "Price Watch" price-drop alert feature. It collected an email + product per watch,
-- but delivery was never wired (no mail provider) and the open, unauthenticated endpoint had no double-opt-in,
-- no guest erasure path and no retention limit — a GDPR liability with no working functionality. The feature and
-- its table are removed. Dropping the table purges every stored email / PII (GDPR Art. 17 erasure). The V4
-- indexes on this table are dropped automatically with it.
DROP TABLE IF EXISTS price_watches;
