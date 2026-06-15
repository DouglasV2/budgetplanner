package ai.budgetspace.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Sprint 10.9 — single place that logs failures and returns a clean, user-facing JSON error instead
 * of a raw stack trace. Two cases:
 * <ul>
 *   <li>{@link IllegalArgumentException} (bad input, e.g. a malformed replace request) → {@code 400}
 *       with the original message (these are already written in Croatian for the user).</li>
 *   <li>anything else → {@code 500} with a generic message; the real cause is logged at error level
 *       so an operator can see it without leaking internals to the client.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        log.warn("Bad request rejected: {}", exception.getMessage());
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Zahtjev nije ispravan."
                : exception.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception exception) {
        log.error("Unhandled error while processing request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Dogodila se neočekivana greška. Pokušaj ponovno za koju minutu."));
    }
}
