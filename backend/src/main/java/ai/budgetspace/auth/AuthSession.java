package ai.budgetspace.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Sprint 10.63 — a server-side session. The opaque {@code token} is the capability stored in the user's HttpOnly
 * cookie; the server is the source of truth for validity, so auto-logout is just the clock against two columns:
 *
 * <ul>
 *   <li>{@code expiresAt} — the absolute lifetime (re-login required after it, no matter how active).</li>
 *   <li>{@code lastSeenAt} — slides on use; a session is also dead after {@code idle-days} with no request.</li>
 * </ul>
 *
 * <p>Because the session lives here (not in a self-contained JWT) it is instantly revocable: logout deletes the
 * row, and an idle/expired row is treated as logged-out and pruned.</p>
 */
@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    /** Opaque, unguessable session token (the cookie value). */
    @Id
    @Column(length = 64)
    private String token;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public AuthSession() {
    }

    public AuthSession(String token, String userId, Instant createdAt, Instant expiresAt, Instant lastSeenAt) {
        this.token = token;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastSeenAt = lastSeenAt;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
