package ai.budgetspace.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sprint 10.63 — auth configuration, read from the environment (same env-driven pattern as the rest of the app).
 *
 * <p>Sprint 10.149: sign-in is the SERVER-SIDE OAuth 2.0 authorization-code (redirect) flow, not the browser GIS
 * One-Tap/FedCM token flow. The redirect flow is immune to Google's per-browser One-Tap "cooldown" (which silently
 * blocked re-sign-in with no error), so it works reliably in every browser. It needs the OAuth client SECRET (to
 * exchange the code) and a registered redirect URI — both are read here. The client id stays public.</p>
 */
@Component
public class AuthProperties {

    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;
    private final String postLoginRedirect;
    private final int sessionTtlDays;
    private final int sessionIdleDays;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthProperties(
            @Value("${budgetspace.auth.google.client-id:}") String googleClientId,
            @Value("${budgetspace.auth.google.client-secret:}") String googleClientSecret,
            @Value("${budgetspace.auth.google.redirect-uri:}") String googleRedirectUri,
            @Value("${budgetspace.auth.post-login-redirect:/}") String postLoginRedirect,
            @Value("${budgetspace.auth.session.ttl-days:30}") int sessionTtlDays,
            @Value("${budgetspace.auth.session.idle-days:7}") int sessionIdleDays,
            @Value("${budgetspace.auth.session.cookie-secure:false}") boolean cookieSecure,
            @Value("${budgetspace.auth.session.cookie-samesite:Lax}") String cookieSameSite) {
        this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
        this.googleClientSecret = googleClientSecret == null ? "" : googleClientSecret.trim();
        this.googleRedirectUri = googleRedirectUri == null ? "" : googleRedirectUri.trim();
        this.postLoginRedirect = (postLoginRedirect == null || postLoginRedirect.isBlank()) ? "/" : postLoginRedirect.trim();
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

    /** The Google OAuth Web client id (public — identifies the app to Google), or "" when not configured. */
    public String googleClientId() {
        return googleClientId;
    }

    /** The Google OAuth client SECRET — required for the server-side code exchange. Never sent to the browser. */
    public String googleClientSecret() {
        return googleClientSecret;
    }

    /** The backend callback URL registered in the OAuth client (Authorized redirect URIs). */
    public String googleRedirectUri() {
        return googleRedirectUri;
    }

    /** Where the backend sends the browser after a successful (or failed) login — the frontend app URL. */
    public String postLoginRedirect() {
        return postLoginRedirect;
    }

    /**
     * True when the full server-side redirect login flow is configured (client id + secret + redirect URI). This
     * is what {@code googleEnabled} reports now: the frontend should only offer "Continue with Google" when the
     * flow will actually complete.
     */
    public boolean redirectLoginConfigured() {
        return !googleClientId.isBlank() && !googleClientSecret.isBlank() && !googleRedirectUri.isBlank();
    }

    /** True when real Google sign-in is available (the redirect flow is fully configured). */
    public boolean googleEnabled() {
        return redirectLoginConfigured();
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
