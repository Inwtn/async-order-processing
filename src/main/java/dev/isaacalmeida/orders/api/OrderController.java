package dev.isaacalmeida.orders.api;

import dev.isaacalmeida.orders.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestHeader("Idempotency-Key") String key,
                                                 @Valid @RequestBody CreateOrderRequest request) {
        var result = service.create(key, request);
        URI location = URI.create("/api/orders/" + result.response().id());
        return ResponseEntity.status(result.replay() ? 200 : 202).location(location).body(result.response());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) { return service.get(id); }
}
