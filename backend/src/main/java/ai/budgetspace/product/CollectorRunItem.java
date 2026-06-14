package ai.budgetspace.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persisted per-URL result of one collector run (Sprint 9.5). Dev/audit only. */
@Entity
@Table(name = "collector_run_items")
public class CollectorRunItem {
    @Id
    private String id;

    @Column(name = "run_id", length = 80)
    private String runId;

    @Column(length = 700)
    private String url;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Column(length = 300)
    private String name;

    @Column(length = 40)
    private String status;

    @Column(name = "data_quality", length = 40)
    private String dataQuality;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String warnings;

    @Column(name = "missing_fields", columnDefinition = "TEXT")
    private String missingFields;

    public CollectorRunItem() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDataQuality() { return dataQuality; }
    public void setDataQuality(String dataQuality) { this.dataQuality = dataQuality; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getWarnings() { return warnings; }
    public void setWarnings(String warnings) { this.warnings = warnings; }
    public String getMissingFields() { return missingFields; }
    public void setMissingFields(String missingFields) { this.missingFields = missingFields; }
}
