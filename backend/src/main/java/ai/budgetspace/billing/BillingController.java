package ai.budgetspace.billing;

import ai.budgetspace.auth.AppUser;
import ai.budgetspace.auth.AuthService;
import ai.budgetspace.dto.BillingConfirmRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sprint 10.69 — Stripe billing endpoints. Checkout + confirm require a signed-in account (you must have an
 * account to subscribe). The webhook is unauthenticated but verified by Stripe's signature inside the service.
 */
@RestController
public class BillingController {
    // Kept in sync with AuthController.COOKIE.
    static final String AUTH_COOKIE = "bs_auth";
    private static final String FALLBACK_BASE = "http://localhost:5180";

    private final AuthService authService;
    private final BillingService billingService;

    public BillingController(AuthService authService, BillingService billingService) {
        this.authService = authService;
        this.billingService = billingService;
    }

    /** Create a Plus subscription Checkout Session and return the hosted-checkout URL to redirect to. */
    @PostMapping("/api/billing/checkout")
    public Map<String, String> checkout(@CookieValue(name = AUTH_COOKIE, required = false) String token,
                                        @RequestHeader(name = "Origin", required = false) String origin) {
        AppUser user = requireUser(token);
        String base = baseUrl(origin);
        String url = billingService.createCheckoutUrl(user,
                base + "/?plus=success&session_id={CHECKOUT_SESSION_ID}",
                base + "/?plus=cancel");
        return Map.of("url", url);
    }

    /** On return from Checkout: verify the session with Stripe and upgrade this account if it's paid. */
    @PostMapping("/api/billing/confirm")
    public Map<String, String> confirm(@CookieValue(name = AUTH_COOKIE, required = false) String token,
                                       @RequestBody(required = false) BillingConfirmRequest request) {
        AppUser user = requireUser(token);
        String plan = billingService.confirmAndUpgrade(request == null ? null : request.sessionId(), user);
        return Map.of("plan", plan);
    }

    /** The production webhook. Stripe sends the raw body + a signature; the service verifies it before acting. */
    @PostMapping("/api/billing/webhook")
    @ResponseStatus(HttpStatus.OK)
    public void webhook(@RequestBody(required = false) String payload,
                        @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        billingService.handleWebhook(payload, signature);
    }

    private AppUser requireUser(String token) {
        return authService.authenticate(token)
                .orElseThrow(() -> new NotSignedInException("Prijava je potrebna za Plus."));
    }

    // Only trust an http(s) Origin set by the browser; otherwise fall back to the dev origin. The CORS filter
    // already restricts which origins can reach this endpoint, so the Origin can't be an arbitrary attacker value.
    private static String baseUrl(String origin) {
        return origin != null && origin.startsWith("http") ? origin : FALLBACK_BASE;
    }

    @ExceptionHandler(NotSignedInException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> onNotSignedIn(NotSignedInException exception) {
        return Map.of("error", "Prijava je potrebna.", "code", "NOT_SIGNED_IN");
    }

    @ExceptionHandler(InvalidWebhookException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String onInvalidWebhook(InvalidWebhookException exception) {
        return "Invalid signature.";
    }

    @ExceptionHandler(BillingUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> onUnavailable(BillingUnavailableException exception) {
        return Map.of("error", "Naplata trenutno nije dostupna.", "code", "BILLING_UNAVAILABLE");
    }
}
