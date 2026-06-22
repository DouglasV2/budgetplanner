package ai.budgetspace.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Sprint 10.86 — the durable form of an {@link AiUsageEvent}. {@code AiUsageEvent} is a record (can't be a JPA
 * entity), so this is its persistent twin: one row per REAL (non-fallback) AI call. The in-memory
 * {@link AiUsageTracker} stays the live, atomic counter source; it write-throughs each real event to this table
 * and rehydrates from it on startup, so the monthly-USD wallet cap and per-user daily caps survive restarts
 * instead of resetting to zero. No prompt text or API key is stored — only metadata, same as the event record.
 */
@Entity
@Table(name = "ai_usage_events")
public class AiUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_key", length = 120)
    private String ownerKey;
    @Column(length = 16)
    private String tier;
    @Column(length = 40)
    private String provider;
    @Column(length = 80)
    private String model;
    @Column(name = "use_case", length = 40)
    private String useCase;
    @Column(name = "input_tokens")
    private Integer inputTokens;
    @Column(name = "output_tokens")
    private Integer outputTokens;
    @Column(name = "estimated_cost_usd", nullable = false)
    private double estimatedCostUsd;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiUsageRecord() {
    }

    /** A persistent row from a real (non-fallback) usage event. */
    static AiUsageRecord from(AiUsageEvent event) {
        AiUsageRecord record = new AiUsageRecord();
        record.ownerKey = event.ownerKey();
        record.tier = event.tier();
        record.provider = event.provider();
        record.model = event.model();
        record.useCase = event.useCase();
        record.inputTokens = event.inputTokens();
        record.outputTokens = event.outputTokens();
        record.estimatedCostUsd = event.estimatedCostUsd();
        record.createdAt = event.createdAt();
        return record;
    }

    /** Back to the in-memory event (these are always real, counted events: success, not fallback). */
    AiUsageEvent toEvent() {
        return new AiUsageEvent(provider, model, useCase, ownerKey, tier, inputTokens, outputTokens,
                estimatedCostUsd, true, false, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
