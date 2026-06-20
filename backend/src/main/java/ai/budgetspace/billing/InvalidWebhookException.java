package ai.budgetspace.billing;

/**
 * Sprint 10.69 — a Stripe webhook failed signature verification (forged or misconfigured). Mapped to 400 so
 * Stripe records the delivery as rejected and never trusts an unverified event.
 */
public class InvalidWebhookException extends RuntimeException {
    public InvalidWebhookException(String message) {
        super(message);
    }
}
