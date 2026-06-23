package ai.budgetspace.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sprint 10.63 — auth configuration, read from the environment (same env-driven pattern as the rest of the app).
 *
 * <p>The Google client id is PUBLIC (it identifies the OAuth app to Google and is embedded in the frontend
 * button); it is not a secret. When it is blank, Google sign-in is dormant and the app is guest-only — no fake
 * logged-in state is ever fabricated. No client secret is read here: we verify the Google ID token locally
 * against Google's published keys, we do not run the server-side authorization-code flow.</p>
 */
@Component
public class AuthProperties {

    private final String googleClientId;
    private final int sessionTtlDays;
    private final int sessionIdleDays;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthProperties(
            @Value("${budgetspace.auth.google.client-id:}") String googleClientId,
            @Value("${budgetspace.auth.session.ttl-days:30}") int sessionTtlDays,
            @Value("${budgetspace.auth.session.idle-days:7}") int sessionIdleDays,
            @Value("${budgetspace.auth.session.cookie-secure:false}") boolean cookieSecure,
            @Value("${budgetspace.auth.session.cookie-samesite:Lax}") String cookieSameSite) {
        this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
        this.sessionTtlDays = sessionTtlDays;
        this.sessionIdleDays = sessionIdleDays;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = normalizeSameSite(cookieSameSite);
    }

    // SameSite is one of Lax / Strict / None (case-insensitive in config; emitted canonically). Anything else
    // falls back to the safe default Lax rather than emitting an invalid attribute the browser would ignore.
    private static String normalizeSameSite(String value) {
        if (value == null) return "Lax";
        return switch (value.trim().toLowerCase()) {
            case "none" -> "None";
            case "strict" -> "Strict";
            default -> "Lax";
        };
    }

    /** The public Google OAuth Web client id, or "" when not configured. */
    public String googleClientId() {
        return googleClientId;
    }

    /** True when a Google client id is configured — i.e. real Google sign-in is available. */
    public boolean googleEnabled() {
        return !googleClientId.isBlank();
    }

    /** Absolute lifetime of a session before it must be re-established. */
    public int sessionTtlDays() {
        return sessionTtlDays;
    }

    /** A session expires after this many days without any authenticated request (auto-logout on inactivity). */
    public int sessionIdleDays() {
        return sessionIdleDays;
    }

    /** Whether the session cookie carries the {@code Secure} flag (must be true in production over HTTPS). */
    public boolean cookieSecure() {
        return cookieSecure;
    }

    /**
     * The session cookie's {@code SameSite} attribute: {@code Lax} (default; frontend and backend share a
     * registrable domain), {@code Strict}, or {@code None} (frontend and backend on unrelated domains — a
     * split-host deploy). {@code None} only works alongside {@code Secure}, so the controller forces Secure on
     * when this is {@code None}.
     */
    public String cookieSameSite() {
        return cookieSameSite;
    }
}
