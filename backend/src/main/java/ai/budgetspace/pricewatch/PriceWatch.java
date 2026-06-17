package ai.budgetspace.pricewatch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

/**
 * Sprint 10.34 — an opt-in price-drop watch. A user asks to be notified by email when a specific
 * product's price falls; a scheduled re-check compares the live price against the {@code baselinePrice}
 * recorded here and, on a real drop past the user's threshold, the {@code PriceWatchNotifier} delivers
 * an alert.
 *
 * <p><strong>GDPR.</strong> The only personal datum stored is the subscriber's {@code email}, and it is
 * stored <em>only</em> with explicit consent ({@code consentAt} records when). Everything else is the
 * minimum needed to send a truthful alert (which product, the baseline price, the threshold) plus a
 * one-click {@code unsubscribeToken}. We keep a small denormalised snapshot of the product (name, url,
 * retailer, baseline) so an alert reads honestly and needs no join even if the catalog later changes.</p>
 */
@Entity
@Table(name = "price_watches")
public class PriceWatch {
    @Id
    private String id;

    // The watched product, by its stable externalId (+ market/retailer for the re-check probe).
    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;
    @Column(length = 8)
    private String market;
    @Column(length = 80)
    private String retailer;
    @Column(name = "product_name", length = 300)
    private String productName;
    @Column(name = "product_url", length = 700)
    private String productUrl;

    // GDPR: the only PII. Stored only after explicit consent (see consentAt).
    @Column(nullable = false, length = 200)
    private String email;
    // Anonymous per-browser id (no PII); lets us associate a watch with a session without login.
    @Column(name = "session_id", length = 80)
    private String sessionId;

    // The price when the watch was created; the re-check compares the live price against this.
    @Column(name = "baseline_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal baselinePrice;
    // Minimum drop (percent) before we notify. Defaults to 5 (owner decision: ignore sub-5% noise).
    @Column(name = "threshold_percent", nullable = false)
    private int thresholdPercent;

    // False after the user unsubscribes; the re-check only looks at active watches.
    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean active;
    // One-click unsubscribe secret, included in every alert (GDPR: easy opt-out).
    @Column(name = "unsubscribe_token", nullable = false, length = 64)
    private String unsubscribeToken;

    @Column(name = "consent_at", length = 40)
    private String consentAt;
    @Column(name = "created_at", length = 40)
    private String createdAt;
    // Frequency cap: we notify at most once per product per cooldown window (owner decision: ~1/week).
    @Column(name = "last_notified_at", length = 40)
    private String lastNotifiedAt;
    // The price we last alerted on, so we never re-notify the same (or a higher) price.
    @Column(name = "last_notified_price", precision = 10, scale = 2)
    private BigDecimal lastNotifiedPrice;

    public PriceWatch() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getRetailer() { return retailer; }
    public void setRetailer(String retailer) { this.retailer = retailer; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductUrl() { return productUrl; }
    public void setProductUrl(String productUrl) { this.productUrl = productUrl; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public BigDecimal getBaselinePrice() { return baselinePrice; }
    public void setBaselinePrice(BigDecimal baselinePrice) { this.baselinePrice = baselinePrice; }
    public int getThresholdPercent() { return thresholdPercent; }
    public void setThresholdPercent(int thresholdPercent) { this.thresholdPercent = thresholdPercent; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getUnsubscribeToken() { return unsubscribeToken; }
    public void setUnsubscribeToken(String unsubscribeToken) { this.unsubscribeToken = unsubscribeToken; }
    public String getConsentAt() { return consentAt; }
    public void setConsentAt(String consentAt) { this.consentAt = consentAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(String lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }
    public BigDecimal getLastNotifiedPrice() { return lastNotifiedPrice; }
    public void setLastNotifiedPrice(BigDecimal lastNotifiedPrice) { this.lastNotifiedPrice = lastNotifiedPrice; }
}
