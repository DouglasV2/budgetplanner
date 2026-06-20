package ai.budgetspace.billing;

/**
 * Sprint 10.69 — billing was requested while Stripe is not configured (no secret key / price id), or the webhook
 * fired without a webhook secret. Mapped to 503 Service Unavailable; the UI falls back to the Plus waitlist.
 */
public class BillingUnavailableException extends RuntimeException {
    public BillingUnavailableException(String message) {
        super(message);
    }
}
