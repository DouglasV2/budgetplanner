package ai.budgetspace.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Persisted header of one collector run (Sprint 9.5). Dev/audit only — not user-facing. */
@Entity
@Table(name = "collector_runs")
public class CollectorRun {
    @Id
    private String id;

    @Column(length = 80)
    private String retailer;

    @Column(name = "started_at", length = 40)
    private String startedAt;

    @Column(name = "finished_at", length = 40)
    private String finishedAt;

    @Column(name = "total_received", nullable = false)
    private int totalReceived;

    @Column(nullable = false)
    private int fetched;

    @Column(nullable = false)
    private int parsed;

    @Column(nullable = false)
    private int imported;

    @Column(nullable = false)
    private int updated;

    @Column(nullable = false)
    private int skipped;

    @Column(name = "needs_review", nullable = false)
    private int needsReview;

    @Column(name = "request_summary", columnDefinition = "TEXT")
    private String requestSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public CollectorRun() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRetailer() { return retailer; }
    public void setRetailer(String retailer) { this.retailer = retailer; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
    public int getTotalReceived() { return totalReceived; }
    public void setTotalReceived(int totalReceived) { this.totalReceived = totalReceived; }
    public int getFetched() { return fetched; }
    public void setFetched(int fetched) { this.fetched = fetched; }
    public int getParsed() { return parsed; }
    public void setParsed(int parsed) { this.parsed = parsed; }
    public int getImported() { return imported; }
    public void setImported(int imported) { this.imported = imported; }
    public int getUpdated() { return updated; }
    public void setUpdated(int updated) { this.updated = updated; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public int getNeedsReview() { return needsReview; }
    public void setNeedsReview(int needsReview) { this.needsReview = needsReview; }
    public String getRequestSummary() { return requestSummary; }
    public void setRequestSummary(String requestSummary) { this.requestSummary = requestSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
