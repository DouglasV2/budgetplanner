package ai.budgetspace.tracking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "product_clicks")
public class ProductClick {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id")
    private String planId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private String retailer;

    @Column(nullable = false)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ProductClick() {
    }

    public ProductClick(String planId, String productId, String retailer, String source, Instant createdAt) {
        this.planId = planId;
        this.productId = productId;
        this.retailer = retailer;
        this.source = source;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getPlanId() { return planId; }
    public String getProductId() { return productId; }
    public String getRetailer() { return retailer; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
}
