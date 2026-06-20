package ai.budgetspace.tracking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Sprint 10.68 — an early willingness-to-pay signal. When a visitor taps "I want Plus" (optionally leaving an
 * email for the launch list), we record it so the owner can gauge demand BEFORE building Stripe billing, and
 * reach interested users when Plus launches. No payment is taken; this is a waitlist + interest counter, not auth.
 */
@Entity
@Table(name = "plus_interest")
public class PlusInterest {
    @Id
    private String id;

    /** The visitor's session/owner key for de-dup context (no account required). Nullable. */
    @Column(name = "session_id", length = 80)
    private String sessionId;

    /** Optional email for the launch list. Nullable — a bare click (no email) is still a valid interest signal. */
    @Column(length = 320)
    private String email;

    /** Where the interest came from (e.g. "pricing", "save-limit"). Nullable. */
    @Column(length = 40)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PlusInterest() {
    }

    public PlusInterest(String id, String sessionId, String email, String source, Instant createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.email = email;
        this.source = source;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getEmail() { return email; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
}
