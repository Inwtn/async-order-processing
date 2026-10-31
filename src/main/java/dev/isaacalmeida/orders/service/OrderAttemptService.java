package dev.isaacalmeida.orders.service;

import dev.isaacalmeida.orders.domain.OrderRepository;
import dev.isaacalmeida.orders.domain.OrderStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderAttemptService {
    private final OrderRepository orders;

    public OrderAttemptService(OrderRepository orders) { this.orders = orders; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int increment(UUID id) {
        var order = orders.findById(id).orElseThrow();
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.FAILED) return -1;
        order.setAttempts(order.getAttempts() + 1);
        order.setStatus(OrderStatus.PROCESSING);
        return order.getAttempts();
    }
}
