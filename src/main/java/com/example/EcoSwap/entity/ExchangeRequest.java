package com.example.EcoSwap.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "exchange_requests")
public class ExchangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offered_product_id", nullable = false)
    private Product offeredProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_product_id", nullable = false)
    private Product requestedProduct;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ExchangeStatus status;

    @Column(name = "cash_adjustment")
    private Double cashAdjustment;

    @Column(name = "delivery_confirmation_level")
    private Integer deliveryConfirmationLevel;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "exchangeRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ExchangeMessage> messages;

    public ExchangeRequest() {
    }

    public ExchangeRequest(Long id, User requester, User owner, Product offeredProduct, Product requestedProduct,
                           String message, ExchangeStatus status, Double cashAdjustment,
                           Integer deliveryConfirmationLevel, LocalDateTime createdAt,
                           LocalDateTime updatedAt, List<ExchangeMessage> messages) {
        this.id = id;
        this.requester = requester;
        this.owner = owner;
        this.offeredProduct = offeredProduct;
        this.requestedProduct = requestedProduct;
        this.message = message;
        this.status = status;
        this.cashAdjustment = cashAdjustment;
        this.deliveryConfirmationLevel = deliveryConfirmationLevel;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messages = messages;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ExchangeStatus.PENDING;
        }
        if (deliveryConfirmationLevel == null) {
            deliveryConfirmationLevel = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int getNormalizedDeliveryLevel() {
        if (deliveryConfirmationLevel == null || deliveryConfirmationLevel < 0) {
            return 0;
        }
        return Math.min(deliveryConfirmationLevel, 4);
    }

    public int getProgressPercent() {
        return getNormalizedDeliveryLevel() * 25;
    }

    public int getWorkflowStageIndex() {
        return getNormalizedDeliveryLevel() + 1;
    }

    public String getWorkflowStageLabel() {
        if (status == ExchangeStatus.REJECTED) {
            return "Da bi tu choi";
        }
        if (status == ExchangeStatus.CANCELLED) {
            return "Da huy giao dich";
        }
        if (status == ExchangeStatus.COMPLETED) {
            return "Hoan thanh";
        }
        if (status == ExchangeStatus.NEGOTIATING && getNormalizedDeliveryLevel() == 0) {
            return "Dang thuong luong va doi admin xac nhan gia";
        }

        return switch (getNormalizedDeliveryLevel()) {
            case 0 -> "Cho admin duyet va dinh gia";
            case 1 -> "Da khop gia trao doi";
            case 2 -> "Cho hai ben gui hang cho admin";
            case 3 -> "Admin dang gui hang trao doi";
            default -> "Hoan thanh";
        };
    }

    public String getStatusLabel() {
        return switch (status) {
            case PENDING -> "Cho admin duyet";
            case NEGOTIATING -> "Dang thuong luong";
            case ACCEPTED -> switch (getNormalizedDeliveryLevel()) {
                case 1 -> "Da khop gia";
                case 2 -> "Cho giao hang cho admin";
                case 3 -> "Admin dang gui hang";
                default -> "Da chap nhan";
            };
            case REJECTED -> "Tu choi";
            case CANCELLED -> "Da huy";
            case COMPLETED -> "Hoan thanh";
        };
    }

    public boolean isTerminal() {
        return status == ExchangeStatus.REJECTED
            || status == ExchangeStatus.CANCELLED
            || status == ExchangeStatus.COMPLETED;
    }

    public boolean canAdvanceWorkflow() {
        return status == ExchangeStatus.ACCEPTED
            && getNormalizedDeliveryLevel() >= 1
            && getNormalizedDeliveryLevel() < 4;
    }

    public boolean isAwaitingAdminReview() {
        return status == ExchangeStatus.PENDING
            || (status == ExchangeStatus.NEGOTIATING && getNormalizedDeliveryLevel() == 0);
    }

    public enum ExchangeStatus {
        PENDING,
        NEGOTIATING,
        ACCEPTED,
        REJECTED,
        CANCELLED,
        COMPLETED
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getRequester() {
        return requester;
    }

    public void setRequester(User requester) {
        this.requester = requester;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Product getOfferedProduct() {
        return offeredProduct;
    }

    public void setOfferedProduct(Product offeredProduct) {
        this.offeredProduct = offeredProduct;
    }

    public Product getRequestedProduct() {
        return requestedProduct;
    }

    public void setRequestedProduct(Product requestedProduct) {
        this.requestedProduct = requestedProduct;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ExchangeStatus getStatus() {
        return status;
    }

    public void setStatus(ExchangeStatus status) {
        this.status = status;
    }

    public Double getCashAdjustment() {
        return cashAdjustment;
    }

    public void setCashAdjustment(Double cashAdjustment) {
        this.cashAdjustment = cashAdjustment;
    }

    public Integer getDeliveryConfirmationLevel() {
        return deliveryConfirmationLevel;
    }

    public void setDeliveryConfirmationLevel(Integer deliveryConfirmationLevel) {
        this.deliveryConfirmationLevel = deliveryConfirmationLevel;
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

    public List<ExchangeMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ExchangeMessage> messages) {
        this.messages = messages;
    }
}
