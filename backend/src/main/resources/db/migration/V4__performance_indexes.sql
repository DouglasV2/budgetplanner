-- Sprint 10.140 (perf): indexes on the columns the app actually filters by. The baseline (V1) only created
-- primary keys + the app_users.google_sub unique, so several hot lookups were sequential scans. At low launch
-- traffic this is invisible; these keep it invisible as rows grow. All IF NOT EXISTS so a prod DB that somehow
-- already has one (e.g. added by hand) migrates cleanly. Hibernate ddl-auto=validate does NOT check indexes, so
-- this never causes schema-drift validation failures.

-- THE hot one: the "Moji planovi" inbox + the Free saved-plan cap count + account-deletion purge all filter by
-- session_id (SavedPlanRepository.findBySessionId.../countBySessionId/deleteByOwner). Runs on essentially every
-- visit, for every visitor.
CREATE INDEX IF NOT EXISTS idx_saved_plans_session_id ON public.saved_plans (session_id);

-- Price-drop watches: the unsubscribe link (findByUnsubscribeToken) and the GDPR erasure / create-dedup, which
-- match on lower(email) — a functional index so `lower(email) = lower(?)` is an index lookup, not a scan.
CREATE INDEX IF NOT EXISTS idx_price_watches_unsubscribe_token ON public.price_watches (unsubscribe_token);
CREATE INDEX IF NOT EXISTS idx_price_watches_email_lower ON public.price_watches (lower(email));

-- Catalog import/dedup (ProductRepository.findByExternalId) — admin path, but external_id is the natural key.
CREATE INDEX IF NOT EXISTS idx_products_external_id ON public.products (external_id);

-- Stripe webhook + reconciliation map an incoming event back to an account by customer/subscription id.
CREATE INDEX IF NOT EXISTS idx_app_users_stripe_customer_id ON public.app_users (stripe_customer_id);
CREATE INDEX IF NOT EXISTS idx_app_users_stripe_subscription_id ON public.app_users (stripe_subscription_id);
