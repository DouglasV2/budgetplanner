package ai.budgetspace.saved;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "saved_plans")
public class SavedPlan {
    @Id
    private String id;

    @Column(name = "plan_json", nullable = false, columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "input_json", nullable = false, columnDefinition = "TEXT")
    private String inputJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean favorite;

    // Sprint 10.53: the owner's browser session (the X-BudgetSpace-Session header). Saved plans are scoped to
    // this so a visitor's "Moji planovi" inbox shows ONLY their own plans — never everyone's. Nullable so a
    // legacy/orphaned row (or a save with no session) simply never matches an inbox query (invisible, not
    // leaked). A real account id replaces this once Google login lands; share-by-link stays open by id.
    @Column(name = "session_id", length = 80)
    private String sessionId;

    public SavedPlan() {
    }

    public SavedPlan(String id, String planJson, String inputJson, Instant createdAt) {
        this(id, planJson, inputJson, createdAt, false);
    }

    public SavedPlan(String id, String planJson, String inputJson, Instant createdAt, boolean favorite) {
        this.id = id;
        this.planJson = planJson;
        this.inputJson = inputJson;
        this.createdAt = createdAt;
        this.favorite = favorite;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public String getInputJson() { return inputJson; }
    public void setInputJson(String inputJson) { this.inputJson = inputJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
