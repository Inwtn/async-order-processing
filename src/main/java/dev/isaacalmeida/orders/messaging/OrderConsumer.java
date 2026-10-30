package dev.isaacalmeida.orders.messaging;

import dev.isaacalmeida.orders.service.OrderAttemptService;
import dev.isaacalmeida.orders.service.OrderProcessingService;
import dev.isaacalmeida.orders.service.OutboxPublisher;
import java.util.UUID;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {
    private final OrderAttemptService attempts;
    private final OrderProcessingService processor;

    public OrderConsumer(OrderAttemptService attempts, OrderProcessingService processor) {
        this.attempts = attempts;
        this.processor = processor;
    }

    @RetryableTopic(kafkaTemplate = "kafkaTemplate", attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2),
            dltProcessingFailureStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = OutboxPublisher.TOPIC, groupId = "order-processing")
    public void consume(String orderId) {
        UUID id = UUID.fromString(orderId);
        int attempt = attempts.increment(id);
        if (attempt > 0) processor.process(id, attempt);
    }

    @DltHandler
    public void deadLetter(String orderId) {
        processor.fail(UUID.fromString(orderId));
    }
}
