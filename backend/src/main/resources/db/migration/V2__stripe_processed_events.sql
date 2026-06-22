-- Flyway V2 (Sprint 10.84) — Stripe webhook idempotency store.
-- One row per Stripe event id we've applied; the webhook handler skips an id it has already seen, so Stripe's
-- at-least-once retries can never double-apply a plan change. Matches the StripeProcessedEvent entity.

CREATE TABLE public.stripe_processed_events (
    event_id character varying(255) NOT NULL,
    event_type character varying(80),
    processed_at timestamp(6) with time zone NOT NULL
);

ALTER TABLE ONLY public.stripe_processed_events
    ADD CONSTRAINT stripe_processed_events_pkey PRIMARY KEY (event_id);
