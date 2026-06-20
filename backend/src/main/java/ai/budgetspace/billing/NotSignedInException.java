package ai.budgetspace.billing;

/**
 * Sprint 10.69 — a billing action (checkout/confirm) was attempted without a valid signed-in session. Mapped to
 * 401 so the frontend prompts sign-in (you must have an account to subscribe to Plus).
 */
public class NotSignedInException extends RuntimeException {
    public NotSignedInException(String message) {
        super(message);
    }
}
