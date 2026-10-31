package dev.isaacalmeida.orders;

import dev.isaacalmeida.orders.api.CreateOrderRequest;
import dev.isaacalmeida.orders.domain.FailureMode;
import dev.isaacalmeida.orders.domain.OrderRepository;
import dev.isaacalmeida.orders.domain.OrderStatus;
import dev.isaacalmeida.orders.domain.OutboxRepository;
import dev.isaacalmeida.orders.service.OrderAttemptService;
import dev.isaacalmeida.orders.service.OrderProcessingService;
import dev.isaacalmeida.orders.service.OrderService;
import dev.isaacalmeida.orders.service.OutboxPublisher;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "app.outbox.interval-ms=3600000"
})
class OrderFlowIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired OutboxRepository outbox;
    @Autowired OrderAttemptService attempts;
    @Autowired OrderProcessingService processor;
    @Autowired OutboxPublisher publisher;
    @MockitoBean(name = "testKafkaTemplate") KafkaTemplate<String, String> kafka;

    @BeforeEach
    void clean() {
        outbox.deleteAll();
        orders.deleteAll();
    }

    private CreateOrderRequest request(FailureMode mode) {
        return new CreateOrderRequest("customer-42", List.of(
                new CreateOrderRequest.Line("BOOK-1", 2, new BigDecimal("19.90")),
                new CreateOrderRequest.Line("PEN-2", 1, new BigDecimal("5.00"))), mode);
    }

    @Test
    void createsOrderAndOutboxAtomicallyAndReplaysSameIdempotencyKey() {
        var first = service.create("order-key-001", request(FailureMode.NONE));
        var replay = service.create("order-key-001", request(FailureMode.NONE));

        assertThat(first.replay()).isFalse();
        assertThat(first.response().total()).isEqualByComparingTo("44.80");
        assertThat(replay.replay()).isTrue();
        assertThat(replay.response().id()).isEqualTo(first.response().id());
        assertThat(orders.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThatThrownBy(() -> service.create("order-key-001", request(FailureMode.PERMANENT)))
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void transientFailureSucceedsOnRetryAndDuplicateProcessingIsSafe() {
        var id = service.create("order-key-002", request(FailureMode.TRANSIENT)).response().id();

        assertThatThrownBy(() -> processor.process(id, attempts.increment(id)))
                .hasMessageContaining("Simulated processor failure");
        processor.process(id, attempts.increment(id));
        processor.process(id, 3);

        var result = service.get(id);
        assertThat(result.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(result.attempts()).isEqualTo(2);
    }

    @Test
    void permanentFailureMovesToFailedAfterRetries() {
        var id = service.create("order-key-003", request(FailureMode.PERMANENT)).response().id();
        for (int retry = 0; retry < 3; retry++) {
            int attempt = attempts.increment(id);
            assertThatThrownBy(() -> processor.process(id, attempt)).isInstanceOf(IllegalStateException.class);
        }
        processor.fail(id);

        var result = service.get(id);
        assertThat(result.status()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(result.failureReason()).contains("retry attempts");
    }

    @Test
    void outboxMarksEventPublishedOnlyAfterKafkaAcknowledgesIt() {
        service.create("order-key-004", request(FailureMode.NONE));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        assertThat(outbox.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).isEmpty();
    }
}
