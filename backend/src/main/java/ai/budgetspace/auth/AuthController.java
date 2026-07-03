package ai.budgetspace.auth;

import ai.budgetspace.dto.AuthMeResponse;
import ai.budgetspace.dto.AuthUserDto;
import ai.budgetspace.dto.GoogleLoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Sprint 10.63 — Google Sign-In + session endpoints. The session is an opaque, HttpOnly cookie, so it is never
 * readable by JavaScript (XSS can't steal it). Its {@code SameSite} attribute is configurable (default {@code Lax}
 * for a same-registrable-domain deploy; {@code None}+Secure for a split-host deploy where the frontend and backend
 * live on unrelated domains) — see {@link AuthProperties#cookieSameSite()}.
 */
@RestController
public class AuthController {

    /** The session cookie name. HttpOnly always; SameSite + Secure are toggled per environment. */
    static final String COOKIE = "bs_auth";
    /** Sprint 10.149 — short-lived CSRF/state cookie for the OAuth redirect flow (also carries the guest id). */
    private static final String STATE_COOKIE = "bs_oauth";
    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final SecureRandom RNG = new SecureRandom();

    private final AuthService authService;
    private final AuthProperties properties;
    private final ai.budgetspace.billing.StripeProperties stripeProperties;
    private final ai.budgetspace.billing.BillingService billingService;
    private final ai.budgetspace.ai.LlmClientFactory clientFactory;
    private final ObjectMapper objectMapper;
    // Sprint 10.105: free-beta switch for the one-time Design Session model. Default true → all premium unlocked
    // for free + beta notice; flip BUDGETSPACE_BETA_MODE=false once one-time payments are wired (no code change).
    private final boolean betaMode;

    public AuthController(AuthService authService, AuthProperties properties,
                          ai.budgetspace.billing.StripeProperties stripeProperties,
                          ai.budgetspace.billing.BillingService billingService,
                          ai.budgetspace.ai.LlmClientFactory clientFactory,
                          ObjectMapper objectMapper,
                          @Value("${budgetspace.beta-mode:true}") boolean betaMode) {
        this.authService = authService;
        this.properties = properties;
        this.stripeProperties = stripeProperties;
        this.billingService = billingService;
        this.clientFactory = clientFactory;
        this.objectMapper = objectMapper;
        this.betaMode = betaMode;
    }

    /** Sign in with a Google ID token. Sets the session cookie and returns the signed-in profile. */
    @PostMapping("/api/auth/google")
    public AuthUserDto google(@RequestBody GoogleLoginRequest body, HttpServletRequest request,
                              HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(
                body == null ? null : body.credential(),
                body == null ? null : body.guestSessionId());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.session().getToken(),
                Duration.ofDays(properties.sessionTtlDays()), request).toString());
        return toDto(result.user());
    }

    /**
     * Sprint 10.149 — start the SERVER-SIDE OAuth redirect login (replaces the browser GIS One-Tap/FedCM flow,
     * which Google silently throttled per-browser after repeated sign-in/out — "Prijava ne radi" with no error).
     * Mints a CSRF {@code state} (carrying the guest session id so first sign-in still claims the guest's saved
     * plans), stores it in a short-lived HttpOnly cookie, and 302s to Google. {@code prompt=select_account} forces
     * the account chooser, so re-sign-in always works — there is no client-side cooldown to get stuck in.
     */
    @GetMapping("/api/auth/google/start")
    public void googleStart(@RequestParam(name = "guest", required = false) String guest,
                            HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!properties.redirectLoginConfigured()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Google prijava nije konfigurirana.");
            return;
        }
        String state = randomToken();
        response.addHeader(HttpHeaders.SET_COOKIE,
                stateCookie(state + "|" + (guest == null ? "" : guest), Duration.ofMinutes(10), request).toString());
        String url = GOOGLE_AUTH_URL
                + "?client_id=" + enc(properties.googleClientId())
                + "&redirect_uri=" + enc(properties.googleRedirectUri())
                + "&response_type=code"
                + "&scope=" + enc("openid email profile")
                + "&state=" + enc(state)
                + "&access_type=online"
                + "&prompt=select_account";
        response.sendRedirect(url);
    }

    /**
     * Sprint 10.149 — Google redirects back here with an authorization code. Verify the CSRF state, exchange the
     * code for tokens SERVER-SIDE (with the client secret), then reuse the normal login path (verify the id_token,
     * upsert the user, migrate guest plans, create the session) and set the session cookie. Always 302s back to the
     * frontend; on any failure it appends {@code ?login=error} and leaks nothing.
     */
    @GetMapping("/api/auth/google/callback")
    public void googleCallback(@RequestParam(name = "code", required = false) String code,
                               @RequestParam(name = "state", required = false) String state,
                               @RequestParam(name = "error", required = false) String error,
                               @CookieValue(name = STATE_COOKIE, required = false) String stateCookie,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        // Always clear the one-time state cookie.
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO, request).toString());

        if (error != null || code == null || code.isBlank() || state == null || stateCookie == null) {
            redirectToFrontend(response, true);
            return;
        }
        String[] parts = stateCookie.split("\\|", 2);
        String guest = parts.length > 1 && !parts[1].isBlank() ? parts[1] : null;
        // Constant-time compare of the returned state against the cookie's — a CSRF guard.
        if (!MessageDigest.isEqual(parts[0].getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            redirectToFrontend(response, true);
            return;
        }
        String idToken;
        try {
            idToken = exchangeCodeForIdToken(code);
        } catch (Exception exception) {
            redirectToFrontend(response, true);
            return;
        }
        try {
            AuthService.LoginResult result = authService.login(idToken, guest);
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.session().getToken(),
                    Duration.ofDays(properties.sessionTtlDays()), request).toString());
        } catch (InvalidGoogleTokenException exception) {
            redirectToFrontend(response, true);
            return;
        }
        redirectToFrontend(response, false);
    }

    private String exchangeCodeForIdToken(String code) throws Exception {
        String body = "code=" + enc(code)
                + "&client_id=" + enc(properties.googleClientId())
                + "&client_secret=" + enc(properties.googleClientSecret())
                + "&redirect_uri=" + enc(properties.googleRedirectUri())
                + "&grant_type=authorization_code";
        HttpRequest request = HttpRequest.newBuilder(URI.create(GOOGLE_TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> httpResponse = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (httpResponse.statusCode() != 200) {
            throw new IllegalStateException("Token exchange failed: HTTP " + httpResponse.statusCode());
        }
        JsonNode json = objectMapper.readTree(httpResponse.body());
        String idToken = json.path("id_token").asText("");
        if (idToken.isBlank()) {
            throw new IllegalStateException("No id_token in the token response.");
        }
        return idToken;
    }

    private void redirectToFrontend(HttpServletResponse response, boolean failed) throws IOException {
        String base = properties.postLoginRedirect();
        response.sendRedirect(failed ? base + (base.contains("?") ? "&" : "?") + "login=error" : base);
    }

    // The CSRF/state cookie. SameSite=Lax (NOT inherited — Strict would be dropped on Google's top-level redirect
    // back to the callback, breaking the flow); Secure follows the session cookie's environment. Fail-safe: also
    // Secure whenever the request actually arrived over HTTPS, even if the env flag was forgotten (can only ADD
    // Secure, never remove it — plain-http dev where request.isSecure()==false stays unaffected).
    private ResponseCookie stateCookie(String value, Duration maxAge, HttpServletRequest request) {
        boolean secure = properties.cookieSecure() || "None".equals(properties.cookieSameSite()) || request.isSecure();
        return ResponseCookie.from(STATE_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Who is signed in (or null), plus whether Google sign-in is available and the public client id to render it. */
    @GetMapping("/api/auth/me")
    public AuthMeResponse me(@CookieValue(name = COOKIE, required = false) String sessionToken) {
        AuthUserDto user = authService.authenticate(sessionToken).map(AuthController::toDto).orElse(null);
        return new AuthMeResponse(user, properties.googleEnabled(),
                properties.googleEnabled() ? properties.googleClientId() : null,
                stripeProperties.configured(), clientFactory.activeClient().isPresent(), betaMode);
    }

    /** Sign out: delete the server session and clear the cookie. */
    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = COOKIE, required = false) String sessionToken,
                       HttpServletRequest request, HttpServletResponse response) {
        authService.logout(sessionToken);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO, request).toString());
    }

    /**
     * Sprint 10.72 — GDPR "right to be forgotten": permanently delete the signed-in account and all its data
     * (saved plans + sessions), then clear the cookie. 401 if not signed in (a guest has no account to delete).
     */
    @DeleteMapping("/api/auth/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@CookieValue(name = COOKIE, required = false) String sessionToken,
                              HttpServletRequest request, HttpServletResponse response) {
        AppUser user = authService.authenticate(sessionToken)
                .orElseThrow(() -> new NotAuthenticatedException("Niste prijavljeni."));
        // Sprint 10.73: cancel any live Stripe subscription first so a deleted account is never billed again
        // (best-effort — never blocks the erasure), then delete the account + all its data.
        billingService.cancelSubscriptionQuietly(user.getStripeSubscriptionId());
        authService.deleteAccount(user);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO, request).toString());
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge, HttpServletRequest request) {
        String sameSite = properties.cookieSameSite();
        // Browsers reject a SameSite=None cookie that isn't also Secure, so force Secure on for a split-host
        // (None) deploy even if cookie-secure wasn't set — otherwise sign-in would silently break cross-site.
        // Fail-safe: also Secure whenever the request actually arrived over HTTPS (request.isSecure()), so the
        // cookie is Secure in prod even if the env flag was forgotten. This can only ADD Secure, never remove it —
        // plain-http local dev (request.isSecure()==false, cookie-secure=false, SameSite=Lax) is unaffected.
        boolean secure = properties.cookieSecure() || "None".equals(sameSite) || request.isSecure();
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private static AuthUserDto toDto(AppUser user) {
        return new AuthUserDto(user.getId(), user.getEmail(), user.getName(), user.getPictureUrl(), user.getPlan());
    }

    // A rejected Google token is a 401; we never say which check failed.
    @ExceptionHandler(InvalidGoogleTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String onInvalidToken(InvalidGoogleTokenException exception) {
        return "Google prijava nije uspjela.";
    }

    // Sprint 10.72: account action attempted without a signed-in session → 401. A LOCAL handler so the global
    // @RestControllerAdvice catch-all (which maps every Exception to 500) never swallows it.
    @ExceptionHandler(NotAuthenticatedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String onNotAuthenticated(NotAuthenticatedException exception) {
        return "Niste prijavljeni.";
    }

    // Google sign-in attempted while it is not configured → unavailable, not a client error. A NARROW exception
    // type (so an unrelated internal IllegalStateException is never mislabeled 503) and a FIXED message (so no
    // internal detail leaks — other errors fall through to GlobalExceptionHandler's safe generic 500).
    @ExceptionHandler(GoogleSignInUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String onNotConfigured(GoogleSignInUnavailableException exception) {
        return "Google prijava trenutno nije dostupna.";
    }
}
