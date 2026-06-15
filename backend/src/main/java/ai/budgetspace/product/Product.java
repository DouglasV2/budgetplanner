package ai.budgetspace.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String retailer;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "style_tags", nullable = false)
    private String styleTags;

    @Column(name = "room_tags", nullable = false)
    private String roomTags;

    @Column(name = "image_url", length = 700)
    private String imageUrl;

    @Column(name = "product_url", length = 700)
    private String productUrl;

    @Column(name = "availability_status", length = 80)
    private String availabilityStatus;

    @Column(name = "delivery_note", length = 300)
    private String deliveryNote;

    @Column(name = "last_checked_at", length = 40)
    private String lastCheckedAt;

    @Column(name = "external_id", length = 120)
    private String externalId;

    @Column(name = "price_tier", length = 40)
    private String priceTier;

    // Catalog source metadata (Sprint 9.0). Backend/dev/docs only — not shown to the user.
    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_name", length = 80)
    private String sourceName;

    @Column(name = "source_reference", length = 300)
    private String sourceReference;

    @Column(name = "imported_at", length = 40)
    private String importedAt;

    @Column(name = "data_quality", length = 40)
    private String dataQuality;

    @Column(name = "data_quality_notes", length = 500)
    private String dataQualityNotes;

    @Column(nullable = false, length = 700)
    private String image;

    @Column(nullable = false, length = 700)
    private String url;

    @Column(nullable = false)
    private double rating;

    @Column(name = "in_stock", nullable = false)
    private boolean inStock;

    @Column(nullable = false, length = 700)
    private String note;

    // --- Sprint 10.7: smart product matching ---
    // Optional comma-separated colour tags using the canonical keys produced by
    // ProductTaxonomy.deriveColorTags / matched against PlannerInputDto.colorPreferences
    // (e.g. "white,grey,green"). Nullable: a product without colour tags is treated as
    // having no colour preference and simply earns no colour bonus.
    @Column(name = "color_tags", length = 200)
    private String colorTags;

    // Optional comma-separated material tags using the canonical keys produced by
    // ProductTaxonomy.deriveMaterialTags (e.g. "wood,metal,glass"). Nullable, same as above.
    @Column(name = "material_tags", length = 200)
    private String materialTags;

    // --- Sprint 10.10: affiliate / sponsored groundwork (no UI ad treatment yet) ---
    // The plain retailer product page, kept separate from any affiliate redirect.
    @Column(name = "original_product_url", length = 700)
    private String originalProductUrl;
    // Optional affiliate/partner redirect URL; when present the UI may use it for the outbound link.
    @Column(name = "affiliate_url", length = 700)
    private String affiliateUrl;
    // A sponsored product must be clearly labelled and never replace the best organic recommendation.
    @Column(name = "is_sponsored", nullable = false)
    private boolean sponsored;
    @Column(name = "sponsor_label", length = 120)
    private String sponsorLabel;

    // --- Sprint 10.13 (#2): reviews. We never fabricate review text; we store the retailer's
    // aggregate (count) when a feed provides it and always link out to the product page where the
    // real reviews live. reviewCount null = unknown. ---
    @Column(name = "review_count")
    private Integer reviewCount;
    @Column(name = "reviews_url", length = 700)
    private String reviewsUrl;

    // --- Sprint 10.13 (#3): market/country this product belongs to (e.g. HR, SI, AT, DE).
    // Null/blank is treated as global (matches any market) so legacy/sample data still works. ---
    @Column(name = "market", length = 8)
    private String market;

    public Product() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRetailer() { return retailer; }
    public void setRetailer(String retailer) { this.retailer = retailer; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
    public String getStyleTags() { return styleTags; }
    public void setStyleTags(String styleTags) { this.styleTags = styleTags; }
    public String getRoomTags() { return roomTags; }
    public void setRoomTags(String roomTags) { this.roomTags = roomTags; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getProductUrl() { return productUrl; }
    public void setProductUrl(String productUrl) { this.productUrl = productUrl; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public String getDeliveryNote() { return deliveryNote; }
    public void setDeliveryNote(String deliveryNote) { this.deliveryNote = deliveryNote; }
    public String getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(String lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getPriceTier() { return priceTier; }
    public void setPriceTier(String priceTier) { this.priceTier = priceTier; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public String getImportedAt() { return importedAt; }
    public void setImportedAt(String importedAt) { this.importedAt = importedAt; }
    public String getDataQuality() { return dataQuality; }
    public void setDataQuality(String dataQuality) { this.dataQuality = dataQuality; }
    public String getDataQualityNotes() { return dataQualityNotes; }
    public void setDataQualityNotes(String dataQualityNotes) { this.dataQualityNotes = dataQualityNotes; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public boolean isInStock() { return inStock; }
    public void setInStock(boolean inStock) { this.inStock = inStock; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getColorTags() { return colorTags; }
    public void setColorTags(String colorTags) { this.colorTags = colorTags; }
    public String getMaterialTags() { return materialTags; }
    public void setMaterialTags(String materialTags) { this.materialTags = materialTags; }
    public String getOriginalProductUrl() { return originalProductUrl; }
    public void setOriginalProductUrl(String originalProductUrl) { this.originalProductUrl = originalProductUrl; }
    public String getAffiliateUrl() { return affiliateUrl; }
    public void setAffiliateUrl(String affiliateUrl) { this.affiliateUrl = affiliateUrl; }
    public boolean isSponsored() { return sponsored; }
    public void setSponsored(boolean sponsored) { this.sponsored = sponsored; }
    public String getSponsorLabel() { return sponsorLabel; }
    public void setSponsorLabel(String sponsorLabel) { this.sponsorLabel = sponsorLabel; }
    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }
    public String getReviewsUrl() { return reviewsUrl; }
    public void setReviewsUrl(String reviewsUrl) { this.reviewsUrl = reviewsUrl; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
}
