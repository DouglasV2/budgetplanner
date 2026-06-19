package ai.budgetspace.auth;

/**
 * Sprint 10.63 — thrown when Google sign-in is attempted while it is not configured (no client id). Mapped to
 * 503 Service Unavailable. Kept distinct from a generic {@link IllegalStateException} so the controller's 503
 * handler stays narrow and never mislabels or echoes an unrelated internal error.
 */
public class GoogleSignInUnavailableException extends RuntimeException {
    public GoogleSignInUnavailableException(String message) {
        super(message);
    }
}
