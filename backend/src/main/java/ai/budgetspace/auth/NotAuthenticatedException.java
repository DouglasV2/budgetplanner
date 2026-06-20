package ai.budgetspace.auth;

/**
 * Sprint 10.72 — raised when an endpoint that requires a signed-in account is hit without a valid session.
 * AuthController maps it to 401 via a LOCAL @ExceptionHandler; a controller-local handler takes precedence over
 * the global @RestControllerAdvice catch-all (which would otherwise turn it into a 500).
 */
public class NotAuthenticatedException extends RuntimeException {
    public NotAuthenticatedException(String message) {
        super(message);
    }
}
