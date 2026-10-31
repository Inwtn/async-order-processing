package dev.isaacalmeida.orders.service;

import dev.isaacalmeida.orders.domain.OutboxRepository;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
    public static final String TOPIC = "orders.created";
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${app.outbox.interval-ms:1000}")
    public void publishPending() {
        for (var event : outbox.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                kafka.send(TOPIC, event.getOrderId().toString(), event.getOrderId().toString())
                        .get(10, TimeUnit.SECONDS);
                event.markPublished();
                outbox.save(event);
            } catch (Exception error) {
                log.warn("Could not publish outbox event {}. It will be retried.", event.getId(), error);
            }
        }
    }
}
