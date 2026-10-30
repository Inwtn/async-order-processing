package dev.isaacalmeida.orders.api;

import dev.isaacalmeida.orders.domain.OrderEntity;
import dev.isaacalmeida.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID id, String customerId, OrderStatus status, BigDecimal total,
                            int attempts, String failureReason, Instant createdAt, Instant updatedAt) {
    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(), order.getTotal(),
                order.getAttempts(), order.getFailureReason(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
