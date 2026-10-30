package dev.isaacalmeida.orders.service;

import dev.isaacalmeida.orders.domain.FailureMode;
import dev.isaacalmeida.orders.domain.OrderRepository;
import dev.isaacalmeida.orders.domain.OrderStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProcessingService {
    private final OrderRepository orders;

    public OrderProcessingService(OrderRepository orders) { this.orders = orders; }

    @Transactional
    public void process(UUID id, int attempt) {
        var order = orders.findById(id).orElseThrow();
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.FAILED) return;
        if (order.getFailureMode() == FailureMode.PERMANENT ||
                (order.getFailureMode() == FailureMode.TRANSIENT && attempt == 1)) {
            throw new IllegalStateException("Simulated processor failure for retry demonstration");
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setFailureReason(null);
    }

    @Transactional
    public void fail(UUID id) {
        var order = orders.findById(id).orElseThrow();
        if (order.getStatus() == OrderStatus.COMPLETED) return;
        order.setStatus(OrderStatus.FAILED);
        order.setFailureReason("Processing failed after all retry attempts");
    }
}
