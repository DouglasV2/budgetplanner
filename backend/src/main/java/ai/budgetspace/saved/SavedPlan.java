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
}
