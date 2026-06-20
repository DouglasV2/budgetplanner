package ai.budgetspace.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByGoogleSub(String googleSub);

    // Sprint 10.69: map a Stripe subscription back to its account (e.g. on a customer.subscription.deleted webhook).
    Optional<AppUser> findByStripeSubscriptionId(String stripeSubscriptionId);
}
