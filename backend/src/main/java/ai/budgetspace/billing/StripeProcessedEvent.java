package ai.budgetspace.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Sprint 10.84 — webhook idempotency. Stripe guarantees <em>at-least-once</em> delivery and retries on any
 * non-2xx/timeout, so the same event id will arrive more than once. We record every event id we've applied; the
 * webhook handler short-circuits on an id it has already seen, so duplicates can never double-apply a plan change
 * (and it's safe to add non-idempotent side-effects later). The Stripe event id is the natural primary key.
 */
@Entity
@Table(name = "stripe_processed_events")
public class StripeProcessedEvent {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "event_type", length = 80)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected StripeProcessedEvent() {
    }

    public StripeProcessedEvent(String eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
