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
}
