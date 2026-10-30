package dev.isaacalmeida.orders.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    private UUID id;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_mode", nullable = false, length = 24)
    private FailureMode failureMode;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_number")
    private List<OrderLine> items = new ArrayList<>();

    protected OrderEntity() {}

    public OrderEntity(String key, String hash, String customerId, FailureMode failureMode, BigDecimal total, List<OrderLine> items) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = key;
        this.requestHash = hash;
        this.customerId = customerId;
        this.status = OrderStatus.RECEIVED;
        this.failureMode = failureMode;
        this.total = total;
        this.items = new ArrayList<>(items);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public FailureMode getFailureMode() { return failureMode; }
    public BigDecimal getTotal() { return total; }
    public int getAttempts() { return attempts; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderLine> getItems() { return items; }

    public void setStatus(OrderStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public void setAttempts(int attempts) { this.attempts = attempts; this.updatedAt = Instant.now(); }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; this.updatedAt = Instant.now(); }
}
