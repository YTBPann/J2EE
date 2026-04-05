package com.example.EcoSwap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double price;

    @Column(name = "approved_price")
    private Double approvedPrice;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(length = 500)
    private String imageUrl;

    @Column(name = "product_condition", length = 50)
    private String condition;

    @Column(length = 50)
    private String status;

    @Column(length = 255)
    private String location;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    public Product() {
    }

    public Product(Long id, String title, String description, Double price, Double approvedPrice,
                   LocalDateTime approvedAt, String imageUrl, String condition, String status,
                   String location, LocalDateTime createdAt, LocalDateTime updatedAt,
                   User user, Category category) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.price = price;
        this.approvedPrice = approvedPrice;
        this.approvedAt = approvedAt;
        this.imageUrl = imageUrl;
        this.condition = condition;
        this.status = status;
        this.location = location;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.user = user;
        this.category = category;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "PENDING_APPROVAL";
        }
        if (condition == null || condition.isBlank()) {
            condition = "USED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isPendingApproval() {
        return "PENDING_APPROVAL".equals(status);
    }

    public boolean isAvailable() {
        return "AVAILABLE".equals(status);
    }

    public boolean isPubliclyVisible() {
        return isAvailable();
    }

    public Double getEffectivePrice() {
        return approvedPrice != null ? approvedPrice : price;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getApprovedPrice() {
        return approvedPrice;
    }

    public void setApprovedPrice(Double approvedPrice) {
        this.approvedPrice = approvedPrice;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}
