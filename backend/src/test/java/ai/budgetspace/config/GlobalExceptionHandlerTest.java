package ai.budgetspace.config;

import ai.budgetspace.saved.SavedPlanNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.98 (security) — the global error mapping. The headline security property: an unexpected exception's real
 * message (which may carry internals/secrets) must NEVER reach the client — only a generic 500. Also locks in that
 * unmapped paths return a quiet 404 (so scanner traffic doesn't become 500s / Sentry noise — Sprint 10.97), and the
 * expected client errors map to their proper 4xx.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedErrorReturnsAGeneric500ThatLeaksNoInternals() {
        ResponseEntity<Map<String, String>> response = handler.handleUnexpected(
                new RuntimeException("NPE in SecretVault: db password=hunter2 at jdbc://prod"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        String body = response.getBody().get("error");
        assertThat(body).doesNotContain("hunter2").doesNotContain("SecretVault").doesNotContain("jdbc");
        assertThat(body).isEqualTo("Dogodila se neočekivana greška. Pokušaj ponovno za koju minutu.");
    }

    @Test
    void unmappedPathReturns404NotAScanNoise500() {
        ResponseEntity<Map<String, String>> response =
                handler.handleNoResource(new NoResourceFoundException(HttpMethod.GET, "/wp-login.php"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void badInputReturns400WithTheUserFacingMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleBadRequest(new IllegalArgumentException("Budžet nije ispravan"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("Budžet nije ispravan");
    }

    @Test
    void missingOrUnauthorizedPlanReturns404() {
        ResponseEntity<Map<String, String>> response =
                handler.handleNotFound(new SavedPlanNotFoundException("gone"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
