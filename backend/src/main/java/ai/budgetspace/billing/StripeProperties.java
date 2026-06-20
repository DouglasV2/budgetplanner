package ai.budgetspace.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sprint 10.69 — Stripe billing config, from the environment (secret key is backend-only, never committed).
 * Billing is {@link #configured() configured} only when a secret key + Plus price id are present; otherwise it is
 * dormant and the UI keeps the honest waitlist. The webhook secret is optional in dev (the confirm-on-redirect
 * path handles the upgrade); it is required for the production webhook.
 */
@Component
public class StripeProperties {
    private final String secretKey;
    private final String publishableKey;
    private final String plusPriceId;
    private final String webhookSecret;

    public StripeProperties(
            @Value("${budgetspace.stripe.secret-key:}") String secretKey,
            @Value("${budgetspace.stripe.publishable-key:}") String publishableKey,
            @Value("${budgetspace.stripe.plus-price-id:}") String plusPriceId,
            @Value("${budgetspace.stripe.webhook-secret:}") String webhookSecret) {
        this.secretKey = trim(secretKey);
        this.publishableKey = trim(publishableKey);
        this.plusPriceId = trim(plusPriceId);
        this.webhookSecret = trim(webhookSecret);
    }

    /** True when checkout can be created (secret key + Plus price id present). */
    public boolean configured() {
        return !secretKey.isBlank() && !plusPriceId.isBlank();
    }

    /** True when the production webhook can verify signatures (webhook secret present). */
    public boolean webhookConfigured() {
        return !webhookSecret.isBlank();
    }

    public String secretKey() { return secretKey; }
    public String publishableKey() { return publishableKey; }
    public String plusPriceId() { return plusPriceId; }
    public String webhookSecret() { return webhookSecret; }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
