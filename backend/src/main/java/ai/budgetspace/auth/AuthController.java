package ai.budgetspace.auth;

import ai.budgetspace.dto.AuthMeResponse;
import ai.budgetspace.dto.AuthUserDto;
import ai.budgetspace.dto.GoogleLoginRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

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

    private final AuthService authService;
    private final AuthProperties properties;
    private final ai.budgetspace.billing.StripeProperties stripeProperties;
    private final ai.budgetspace.billing.BillingService billingService;
    private final ai.budgetspace.ai.LlmClientFactory clientFactory;
    // Sprint 10.105: free-beta switch for the one-time Design Session model. Default true → all premium unlocked
    // for free + beta notice; flip BUDGETSPACE_BETA_MODE=false once one-time payments are wired (no code change).
    private final boolean betaMode;

    public AuthController(AuthService authService, AuthProperties properties,
                          ai.budgetspace.billing.StripeProperties stripeProperties,
                          ai.budgetspace.billing.BillingService billingService,
                          ai.budgetspace.ai.LlmClientFactory clientFactory,
                          @Value("${budgetspace.beta-mode:true}") boolean betaMode) {
        this.authService = authService;
        this.properties = properties;
        this.stripeProperties = stripeProperties;
        this.billingService = billingService;
        this.clientFactory = clientFactory;
        this.betaMode = betaMode;
    }

    /** Sign in with a Google ID token. Sets the session cookie and returns the signed-in profile. */
    @PostMapping("/api/auth/google")
    public AuthUserDto google(@RequestBody GoogleLoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(
                request == null ? null : request.credential(),
                request == null ? null : request.guestSessionId());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.session().getToken(),
                Duration.ofDays(properties.sessionTtlDays())).toString());
        return toDto(result.user());
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
    public void logout(@CookieValue(name = COOKIE, required = false) String sessionToken, HttpServletResponse response) {
        authService.logout(sessionToken);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
    }

    /**
     * Sprint 10.72 — GDPR "right to be forgotten": permanently delete the signed-in account and all its data
     * (saved plans + sessions), then clear the cookie. 401 if not signed in (a guest has no account to delete).
     */
    @DeleteMapping("/api/auth/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@CookieValue(name = COOKIE, required = false) String sessionToken,
                              HttpServletResponse response) {
        AppUser user = authService.authenticate(sessionToken)
                .orElseThrow(() -> new NotAuthenticatedException("Niste prijavljeni."));
        // Sprint 10.73: cancel any live Stripe subscription first so a deleted account is never billed again
        // (best-effort — never blocks the erasure), then delete the account + all its data.
        billingService.cancelSubscriptionQuietly(user.getStripeSubscriptionId());
        authService.deleteAccount(user);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        String sameSite = properties.cookieSameSite();
        // Browsers reject a SameSite=None cookie that isn't also Secure, so force Secure on for a split-host
        // (None) deploy even if cookie-secure wasn't set — otherwise sign-in would silently break cross-site.
        boolean secure = properties.cookieSecure() || "None".equals(sameSite);
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
