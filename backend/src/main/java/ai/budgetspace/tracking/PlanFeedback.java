package ai.budgetspace.tracking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "plan_feedback")
public class PlanFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private String planId;

    @Column(nullable = false)
    private String feedback;

    @Column(length = 700)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PlanFeedback() {
    }

    public PlanFeedback(String planId, String feedback, String note, Instant createdAt) {
        this.planId = planId;
        this.feedback = feedback;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getPlanId() { return planId; }
    public String getFeedback() { return feedback; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
