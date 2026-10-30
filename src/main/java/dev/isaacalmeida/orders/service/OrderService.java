package dev.isaacalmeida.orders.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.isaacalmeida.orders.api.CreateOrderRequest;
import dev.isaacalmeida.orders.api.OrderResponse;
import dev.isaacalmeida.orders.domain.FailureMode;
import dev.isaacalmeida.orders.domain.OrderEntity;
import dev.isaacalmeida.orders.domain.OrderLine;
import dev.isaacalmeida.orders.domain.OrderRepository;
import dev.isaacalmeida.orders.domain.OutboxEvent;
import dev.isaacalmeida.orders.domain.OutboxRepository;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    public OrderService(OrderRepository orders, OutboxRepository outbox, ObjectMapper mapper) {
        this.orders = orders;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public CreateResult create(String key, CreateOrderRequest request) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 8–128 letters, numbers, hyphens, or underscores");
        }
        String hash = hash(request);
        var existing = orders.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used for another request");
            }
            return new CreateResult(OrderResponse.from(existing.get()), true);
        }
        FailureMode mode = request.failureMode() == null ? FailureMode.NONE : request.failureMode();
        var lines = request.items().stream()
                .map(item -> new OrderLine(item.sku(), item.quantity(), item.unitPrice().setScale(2)))
                .toList();
        BigDecimal total = lines.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var order = orders.save(new OrderEntity(key, hash, request.customerId(), mode, total, lines));
        outbox.save(new OutboxEvent(order.getId()));
        return new CreateResult(OrderResponse.from(order), false);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return OrderResponse.from(orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")));
    }

    private String hash(CreateOrderRequest request) {
        try {
            byte[] json = mapper.writeValueAsBytes(request);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not hash order request", e);
        }
    }

    public record CreateResult(OrderResponse response, boolean replay) {}
}
