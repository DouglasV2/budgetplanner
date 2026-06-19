package ai.budgetspace.auth;

/**
 * Sprint 10.63 — thrown when a Google ID token fails verification (bad signature, wrong audience/issuer, expired,
 * or malformed). The controller maps it to a 401 with a generic message; we never leak why a token was rejected.
 */
public class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException(String message) {
        super(message);
    }

    public InvalidGoogleTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
