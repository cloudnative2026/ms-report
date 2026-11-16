package com.cloudnative.ms_report.service;

import com.cloudnative.ms_report.config.KafkaConfig;
import com.cloudnative.ms_report.dto.OrderEventMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderEventConsumer {
    private final OrderProjection projection;
    private final JsonMapper json;

    public OrderEventConsumer(OrderProjection projection, JsonMapper json) {
        this.projection = projection;
        this.json = json;
    }

    // Failures are retried and then sent to the DLT by KafkaConfig's error handler.
    @KafkaListener(topics = KafkaConfig.SOURCE_TOPIC)
    public void consume(String value) {
        projection.apply(json.readValue(value, OrderEventMessage.class));
    }
}
