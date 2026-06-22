package ai.budgetspace.billing;

import org.springframework.data.jpa.repository.JpaRepository;

/** Webhook idempotency store (Sprint 10.84): keyed by the Stripe event id. */
public interface StripeProcessedEventRepository extends JpaRepository<StripeProcessedEvent, String> {
}
