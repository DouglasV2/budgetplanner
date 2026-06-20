package ai.budgetspace.billing;

import ai.budgetspace.auth.AppUser;
import ai.budgetspace.auth.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * Sprint 10.69 — Stripe billing for Plus (hosted Checkout). Raw JDK HTTP, no SDK (consistent with the eBay/LLM
 * clients). Three jobs:
 *
 * <ul>
 *   <li><strong>createCheckoutUrl</strong> — make a subscription Checkout Session for the Plus price, tagged with
 *       the signed-in account id ({@code client_reference_id}); the frontend redirects to the returned URL.</li>
 *   <li><strong>confirmAndUpgrade</strong> — on return from Checkout, retrieve the session from Stripe
 *       (authoritative), and if it's paid AND belongs to this account, flip the account to Plus. This makes dev
 *       work without a public webhook, and is an idempotent backstop in prod.</li>
 *   <li><strong>handleWebhook</strong> — the production path: verify Stripe's signature (HMAC-SHA256, timing-safe,
 *       timestamp tolerance), then upgrade on {@code checkout.session.completed} / downgrade on
 *       {@code customer.subscription.deleted}. Dormant until a webhook secret is configured.</li>
 * </ul>
 *
 * <p>The secret key is sent only as the Stripe Bearer token — never logged or returned to the client.</p>
 */
@Service
public class BillingService {
    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final String API_BASE = "https://api.stripe.com";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300;

    private final StripeProperties properties;
    private final AppUserRepository userRepository;
    private final StripeHttp http;
    private final ObjectMapper objectMapper;

    // @Autowired marks this as THE constructor for Spring; without it the package-private test constructor below
    // makes the bean ambiguous and boot fails with "No default constructor found" (same trap as GoogleTokenVerifier).
    @Autowired
    public BillingService(StripeProperties properties, AppUserRepository userRepository) {
        this(properties, userRepository, new HttpClientStripeHttp(), new ObjectMapper());
    }

    // Package-private: lets a test inject a fake transport (and exercise mapping + signature) without a network.
    BillingService(StripeProperties properties, AppUserRepository userRepository, StripeHttp http, ObjectMapper objectMapper) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.http = http;
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        return properties.configured();
    }

    /** Creates a subscription Checkout Session for this user and returns the hosted-checkout URL to redirect to. */
    public String createCheckoutUrl(AppUser user, String successUrl, String cancelUrl) {
        if (!properties.configured()) {
            throw new BillingUnavailableException("Stripe nije konfiguriran.");
        }
        StringBuilder form = new StringBuilder();
        field(form, "mode", "subscription");
        field(form, "line_items[0][price]", properties.plusPriceId());
        field(form, "line_items[0][quantity]", "1");
        field(form, "success_url", successUrl);
        field(form, "cancel_url", cancelUrl);
        field(form, "client_reference_id", user.getId());
        field(form, "subscription_data[metadata][userId]", user.getId());
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
            field(form, "customer", user.getStripeCustomerId());
        } else if (user.getEmail() != null && !user.getEmail().isBlank()) {
            field(form, "customer_email", user.getEmail());
        }
        try {
            JsonNode session = objectMapper.readTree(http.post("/v1/checkout/sessions", form.toString(), properties.secretKey()));
            String url = session.path("url").asText("");
            if (url.isBlank()) {
                throw new IllegalStateException("Stripe checkout session bez URL-a.");
            }
            return url;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Stripe checkout session creation failed.", exception);
            throw new IllegalStateException("Stripe checkout nije uspio.", exception);
        }
    }

    /**
     * Retrieves the Checkout Session from Stripe and, if it is paid and owned by this user, upgrades them to Plus.
     * Returns the (possibly updated) plan. Idempotent — re-confirming an already-upgraded user is a no-op.
     */
    @Transactional
    public String confirmAndUpgrade(String sessionId, AppUser user) {
        if (!properties.configured() || sessionId == null || sessionId.isBlank()) {
            return user.getPlan();
        }
        JsonNode session;
        try {
            session = objectMapper.readTree(http.get("/v1/checkout/sessions/" + urlPath(sessionId), properties.secretKey()));
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Stripe checkout session retrieval failed.", exception);
            return user.getPlan();
        }
        String paymentStatus = session.path("payment_status").asText("");
        String owner = session.path("client_reference_id").asText("");
        boolean paid = "paid".equals(paymentStatus) || "no_payment_required".equals(paymentStatus);
        // Only upgrade if the session is paid AND was created for THIS account (never trust the session id alone).
        if (paid && user.getId().equals(owner)) {
            applyPlus(user, text(session, "customer"), text(session, "subscription"));
        }
        return user.getPlan();
    }

    /** Verifies Stripe's signature, then upgrades/downgrades on the relevant subscription events. */
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        if (!properties.webhookConfigured()) {
            throw new BillingUnavailableException("Webhook nije konfiguriran.");
        }
        if (!signatureValid(payload, signatureHeader, properties.webhookSecret())) {
            throw new InvalidWebhookException("Neispravan potpis webhooka.");
        }
        JsonNode event;
        try {
            event = objectMapper.readTree(payload == null ? "{}" : payload);
        } catch (IOException exception) {
            throw new InvalidWebhookException("Neispravan webhook payload.");
        }
        String type = event.path("type").asText("");
        JsonNode object = event.path("data").path("object");
        switch (type) {
            case "checkout.session.completed" -> {
                String owner = object.path("client_reference_id").asText("");
                userRepository.findById(owner).ifPresent(user ->
                        applyPlus(user, text(object, "customer"), text(object, "subscription")));
            }
            case "customer.subscription.deleted" -> {
                String subscriptionId = object.path("id").asText("");
                userRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(this::applyFree);
            }
            default -> log.debug("Ignoring Stripe webhook event type: {}", type);
        }
    }

    private void applyPlus(AppUser user, String customerId, String subscriptionId) {
        user.setPlan("PLUS");
        if (customerId != null && !customerId.isBlank()) user.setStripeCustomerId(customerId);
        if (subscriptionId != null && !subscriptionId.isBlank()) user.setStripeSubscriptionId(subscriptionId);
        userRepository.save(user);
        log.info("Account upgraded to PLUS via Stripe (user={}).", user.getId());
    }

    private void applyFree(AppUser user) {
        user.setPlan("FREE");
        user.setStripeSubscriptionId(null);
        userRepository.save(user);
        log.info("Account downgraded to FREE — Stripe subscription ended (user={}).", user.getId());
    }

    // --- Stripe webhook signature (HMAC-SHA256 over "<timestamp>.<payload>"); package-private for tests ---

    boolean signatureValid(String payload, String signatureHeader, String secret) {
        if (payload == null || signatureHeader == null || secret == null || secret.isBlank()) {
            return false;
        }
        String timestamp = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if ("t".equals(key)) timestamp = value;
            else if ("v1".equals(key) && v1 == null) v1 = value;
        }
        if (timestamp == null || v1 == null) {
            return false;
        }
        try {
            long ts = Long.parseLong(timestamp);
            // We can't use a wall clock in tests deterministically, but in prod reject stale signatures (replay).
            long now = System.currentTimeMillis() / 1000L;
            if (Math.abs(now - ts) > SIGNATURE_TOLERANCE_SECONDS) {
                return false;
            }
        } catch (NumberFormatException exception) {
            return false;
        }
        String expected = hmacSha256Hex(secret, timestamp + "." + payload);
        return constantTimeEquals(expected, v1);
    }

    private static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC failed", exception);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    // --- helpers ---

    private static void field(StringBuilder form, String key, String value) {
        if (value == null) return;
        if (form.length() > 0) form.append('&');
        form.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static String urlPath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    // --- HTTP transport (injectable so the mapping + signature can be tested without a network) ---

    interface StripeHttp {
        String post(String path, String formBody, String secretKey) throws IOException, InterruptedException;
        String get(String path, String secretKey) throws IOException, InterruptedException;
    }

    static final class HttpClientStripeHttp implements StripeHttp {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

        @Override
        public String post(String path, String formBody, String secretKey) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + path)).timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                    .build();
            return send(request);
        }

        @Override
        public String get(String path, String secretKey) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + path)).timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + secretKey)
                    .GET()
                    .build();
            return send(request);
        }

        private String send(HttpRequest request) throws IOException, InterruptedException {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Stripe API HTTP " + response.statusCode());
            }
            return response.body();
        }
    }
}
