package ai.budgetspace.billing;

import ai.budgetspace.auth.AppUser;
import ai.budgetspace.auth.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
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
 * Sprint 10.69 — the security-critical parts of Stripe billing: webhook signature verification, the owner check
 * before upgrading on confirm, and the checkout form. No network: a fake transport replays canned Stripe JSON.
 */
class BillingServiceTest {

    private static final String SECRET_KEY = "sk_test_x";
    private static final String WEBHOOK_SECRET = "whsec_test";

    private AppUserRepository userRepository;
    private FakeHttp http;
    private BillingService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        http = new FakeHttp();
        StripeProperties properties = new StripeProperties(SECRET_KEY, "pk_test_x", "price_x", WEBHOOK_SECRET);
        service = new BillingService(properties, userRepository, http, new ObjectMapper());
    }

    @Test
    void createsACheckoutSessionForThePlusPriceTaggedWithTheAccount() {
        http.postResponse = "{\"id\":\"cs_1\",\"url\":\"https://checkout.stripe.com/c/pay/cs_1\"}";
        AppUser user = user("u1", "ana@example.com", "FREE");

        String url = service.createCheckoutUrl(user, "https://app/ok", "https://app/cancel");

        assertThat(url).isEqualTo("https://checkout.stripe.com/c/pay/cs_1");
        assertThat(http.lastPostBody).contains("mode=subscription").contains("price_x").contains("client_reference_id=u1");
    }

    @Test
    void confirmUpgradesWhenPaidAndOwnedByThisAccount() {
        http.getResponse = "{\"payment_status\":\"paid\",\"client_reference_id\":\"u1\",\"customer\":\"cus_1\",\"subscription\":\"sub_1\"}";
        AppUser user = user("u1", "a@b.com", "FREE");

        String plan = service.confirmAndUpgrade("cs_1", user);

        assertThat(plan).isEqualTo("PLUS");
        assertThat(user.getStripeCustomerId()).isEqualTo("cus_1");
        assertThat(user.getStripeSubscriptionId()).isEqualTo("sub_1");
        verify(userRepository).save(user);
    }

    @Test
    void confirmDoesNotUpgradeASessionOwnedByAnotherAccount() {
        // Security: even with a valid (paid) session id, the session must belong to THIS account to upgrade it.
        http.getResponse = "{\"payment_status\":\"paid\",\"client_reference_id\":\"someone-else\",\"subscription\":\"sub_1\"}";
        AppUser user = user("u1", "a@b.com", "FREE");

        assertThat(service.confirmAndUpgrade("cs_1", user)).isEqualTo("FREE");
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmDoesNotUpgradeAnUnpaidSession() {
        http.getResponse = "{\"payment_status\":\"unpaid\",\"client_reference_id\":\"u1\"}";
        AppUser user = user("u1", "a@b.com", "FREE");

        assertThat(service.confirmAndUpgrade("cs_1", user)).isEqualTo("FREE");
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifiesAGenuineStripeSignatureAndRejectsForgeryAndReplay() throws Exception {
        String payload = "{\"type\":\"checkout.session.completed\"}";
        long now = Instant.now().getEpochSecond();
        String good = "t=" + now + ",v1=" + hmacHex(WEBHOOK_SECRET, now + "." + payload);
        assertThat(service.signatureValid(payload, good, WEBHOOK_SECRET)).isTrue();

        // forged signature
        assertThat(service.signatureValid(payload, "t=" + now + ",v1=deadbeef", WEBHOOK_SECRET)).isFalse();
        // wrong secret
        assertThat(service.signatureValid(payload, good, "whsec_other")).isFalse();
        // replay: a correctly-signed but stale (>5 min old) timestamp is rejected
        long old = now - 1000;
        String stale = "t=" + old + ",v1=" + hmacHex(WEBHOOK_SECRET, old + "." + payload);
        assertThat(service.signatureValid(payload, stale, WEBHOOK_SECRET)).isFalse();
        // malformed header
        assertThat(service.signatureValid(payload, "garbage", WEBHOOK_SECRET)).isFalse();
    }

    @Test
    void webhookUpgradesOnAVerifiedCheckoutCompleted() throws Exception {
        String payload = "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":"
                + "{\"client_reference_id\":\"u1\",\"customer\":\"cus_1\",\"subscription\":\"sub_1\"}}}";
        long now = Instant.now().getEpochSecond();
        String header = "t=" + now + ",v1=" + hmacHex(WEBHOOK_SECRET, now + "." + payload);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1", "a@b.com", "FREE")));

        service.handleWebhook(payload, header);

        verify(userRepository).save(any(AppUser.class));
    }

    @Test
    void webhookRejectsAnInvalidSignature() {
        assertThatThrownBy(() -> service.handleWebhook("{}", "t=1,v1=bad"))
                .isInstanceOf(InvalidWebhookException.class);
        verify(userRepository, never()).save(any());
    }

    private static AppUser user(String id, String email, String plan) {
        AppUser user = new AppUser(id, "sub-" + id, email, "Name", "pic", Instant.now(), Instant.now());
        user.setPlan(plan);
        return user;
    }

    private static String hmacHex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /** A fake Stripe transport: captures the last POST body, returns canned JSON. */
    private static final class FakeHttp implements BillingService.StripeHttp {
        String postResponse = "{}";
        String getResponse = "{}";
        String lastPostBody;

        @Override
        public String post(String path, String formBody, String secretKey) {
            this.lastPostBody = formBody;
            return postResponse;
        }

        @Override
        public String get(String path, String secretKey) {
            return getResponse;
        }
    }
}
