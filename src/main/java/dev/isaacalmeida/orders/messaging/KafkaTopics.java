package dev.isaacalmeida.orders.messaging;

import dev.isaacalmeida.orders.service.OutboxPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {
    @Bean
    NewTopic ordersCreatedTopic() {
        return TopicBuilder.name(OutboxPublisher.TOPIC).partitions(1).replicas(1).build();
    }
}
