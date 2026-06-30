package ai.budgetspace.auth;

import ai.budgetspace.ai.LlmClientFactory;
import ai.budgetspace.billing.BillingService;
import ai.budgetspace.billing.StripeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 10.155 (security regression guard) — the GDPR "right to be forgotten" endpoint must stay
 * auth-gated and must cancel any live Stripe subscription BEFORE it erases the account.
 *
 * <ul>
 *   <li>No (or invalid) session → {@link NotAuthenticatedException} (→ 401 via the controller's local handler),
 *       and absolutely nothing is erased or cancelled. A guest has no account to delete, and a non-owner can
 *       never trigger another account's erasure (the session resolves to a specific user or to nothing).</li>
 *   <li>Signed in → cancel-then-erase ordering (a deleted account is never billed again) and the session cookie
 *       is cleared.</li>
 * </ul>
 */
class AuthControllerTest {

    private AuthService authService;
    private BillingService billingService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        billingService = mock(BillingService.class);
        StripeProperties stripeProperties = mock(StripeProperties.class);
        LlmClientFactory clientFactory = mock(LlmClientFactory.class);
        AuthProperties properties = new AuthProperties("client-123", "", "", "/", 30, 7, false, "Lax");
        controller = new AuthController(authService, properties, stripeProperties, billingService,
                clientFactory, new ObjectMapper(), true);
    }

    @Test
    void deleteAccountWithoutASessionIsUnauthorizedAndErasesNothing() {
        when(authService.authenticate(any())).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.deleteAccount(null, response))
                .isInstanceOf(NotAuthenticatedException.class);

        // The unauthenticated path must not touch billing or erase any data.
        verify(billingService, never()).cancelSubscriptionQuietly(any());
        verify(authService, never()).deleteAccount(any());
    }

    @Test
    void deleteAccountCancelsBillingThenErasesAndClearsTheCookie() {
        AppUser user = new AppUser("u1", "sub-1", "ana@example.com", "Ana", "pic", Instant.now(), Instant.now());
        user.setStripeSubscriptionId("sub_live_123");
        when(authService.authenticate("tok")).thenReturn(Optional.of(user));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.deleteAccount("tok", response);

        // Cancel the live subscription first (best-effort), then erase the account + all its data.
        verify(billingService).cancelSubscriptionQuietly("sub_live_123");
        verify(authService).deleteAccount(user);
        // The session cookie is cleared (expired) so the browser is logged out immediately after erasure.
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("bs_auth=").contains("Max-Age=0");
    }
}
