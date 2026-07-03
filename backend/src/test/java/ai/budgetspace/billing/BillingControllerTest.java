package ai.budgetspace.billing;

import ai.budgetspace.auth.AuthService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Sprint 10.163 (config-landmine guard) — while the free beta is on ({@code budgetspace.beta-mode=true}, the
 * default), billing is a hard no-op on the server regardless of Stripe config: checkout/confirm fail with 503
 * BILLING_UNAVAILABLE and the webhook is swallowed. This makes the "free beta" promise true by construction — a
 * stray Stripe key can never charge a user before one-time payments are deliberately turned on.
 */
class BillingControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final BillingService billingService = mock(BillingService.class);

    @Test
    void checkoutIsUnavailableWhileBetaModeIsOn() {
        BillingController controller = new BillingController(authService, billingService, true);

        // 503 BILLING_UNAVAILABLE, and the service is never touched (no Stripe call, no charge).
        assertThatThrownBy(() -> controller.checkout(null, "https://app.example.com"))
                .isInstanceOf(BillingUnavailableException.class);
        verify(billingService, never()).createCheckoutUrl(any(), anyString(), anyString());
    }

    @Test
    void confirmIsUnavailableWhileBetaModeIsOn() {
        BillingController controller = new BillingController(authService, billingService, true);

        assertThatThrownBy(() -> controller.confirm(null, null))
                .isInstanceOf(BillingUnavailableException.class);
        verify(billingService, never()).confirmAndUpgrade(any(), any());
    }

    @Test
    void webhookIsANoOpWhileBetaModeIsOn() {
        BillingController controller = new BillingController(authService, billingService, true);

        // 200 OK, no exception, and the webhook is never processed (no plan can flip during beta).
        assertThatCode(() -> controller.webhook("{}", "sig")).doesNotThrowAnyException();
        verify(billingService, never()).handleWebhook(anyString(), anyString());
    }
}
