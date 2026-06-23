package ai.budgetspace.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByGoogleSub(String googleSub);

    // Sprint 10.69: map a Stripe subscription back to its account (e.g. on a customer.subscription.deleted webhook).
    Optional<AppUser> findByStripeSubscriptionId(String stripeSubscriptionId);

    // Sprint 10.84: fallback when the subscription id wasn't stored (it can be null at checkout.session.completed)
    // — resolve the account by Stripe customer id on subscription lifecycle webhooks.
    Optional<AppUser> findByStripeCustomerId(String stripeCustomerId);

    // Sprint 10.98: every account that has a Stripe subscription — the reconciliation sweep re-checks each one's
    // live status against Stripe to repair drift from any dropped/missed webhook.
    List<AppUser> findByStripeSubscriptionIdIsNotNull();
}
