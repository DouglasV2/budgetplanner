package ai.budgetspace.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Sprint 10.63 — a signed-in account, identified by the Google subject ({@code sub}). We store only what we
 * need to greet the user and own their saved plans: the stable Google sub, their email and display name, and a
 * picture URL. No tokens, no Google account/marketplace data beyond this profile.
 *
 * <p>Named {@code app_users} (not {@code users}) because {@code user} is a reserved word in PostgreSQL.</p>
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    private String id;

    /** The Google {@code sub} claim — the stable, unique identifier of the Google account. */
    @Column(name = "google_sub", nullable = false, unique = true, length = 64)
    private String googleSub;

    @Column(length = 320)
    private String email;

    @Column(length = 200)
    private String name;

    @Column(name = "picture_url", length = 1024)
    private String pictureUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    // Sprint 10.68: monetization tier. "FREE" (default) or "PLUS". Plus unlocks unlimited saved plans, the AI
    // assistant, alerts and export. Stored as a string (not an enum) so adding a tier later needs no migration.
    @Column(name = "plan", length = 16, nullable = false)
    private String plan = "FREE";

    // Sprint 10.69: Stripe identifiers, set when the user subscribes to Plus. Nullable (a Free user has none).
    @Column(name = "stripe_customer_id", length = 80)
    private String stripeCustomerId;
    @Column(name = "stripe_subscription_id", length = 80)
    private String stripeSubscriptionId;

    public AppUser() {
    }

    public AppUser(String id, String googleSub, String email, String name, String pictureUrl, Instant createdAt, Instant lastLoginAt) {
        this.id = id;
        this.googleSub = googleSub;
        this.email = email;
        this.name = name;
        this.pictureUrl = pictureUrl;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    /** The owner key used to scope saved plans to this account (stable across browsers/sessions). */
    public String ownerKey() {
        return "user:" + id;
    }

    /** True when this account is on the paid Plus tier. */
    public boolean isPlus() {
        return "PLUS".equalsIgnoreCase(plan);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGoogleSub() { return googleSub; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPictureUrl() { return pictureUrl; }
    public void setPictureUrl(String pictureUrl) { this.pictureUrl = pictureUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }
}
