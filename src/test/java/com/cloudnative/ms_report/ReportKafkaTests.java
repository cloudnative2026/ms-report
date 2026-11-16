package com.cloudnative.ms_report;

import java.time.Duration;
import java.util.Map;
import com.cloudnative.ms_report.config.KafkaConfig;
import com.cloudnative.ms_report.repository.OrderFactRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.datasource.url=jdbc:h2:mem:report-kafka;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@EmbeddedKafka(partitions = 3, topics = KafkaConfig.SOURCE_TOPIC)
@ActiveProfiles("test")
class ReportKafkaTests {
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired OrderFactRepository events;
    @Autowired EmbeddedKafkaBroker broker;

    @Test void validRecordsArePersistedAndMalformedOnesGoToTheDeadLetterTopic() throws Exception {
        kafka.send(KafkaConfig.SOURCE_TOPIC, "7", """
            {"eventId":"k1","type":"OrderCreated","occurredAt":"2026-11-01T10:00:00Z","orderId":7,"customerId":101,
             "status":"CREADO","totalAmount":20.50,"items":[{"productId":1,"quantity":2,"unitPrice":10.25}],
             "createdAt":"2026-11-01T10:00:00Z"}
            """).get();
        long deadline = System.currentTimeMillis() + 15_000;
        while (!events.existsById(7L) && System.currentTimeMillis() < deadline) Thread.sleep(100);
        assertThat(events.findById(7L)).hasValueSatisfying(f -> assertThat(f.getStatus()).isEqualTo("CREADO"));

        kafka.send(KafkaConfig.SOURCE_TOPIC, "bad", "{not json").get();
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "dlt-reader", false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer()) {
            consumer.subscribe(java.util.List.of(KafkaConfig.DEAD_LETTER_TOPIC));
            var record = KafkaTestUtils.getSingleRecord(consumer, KafkaConfig.DEAD_LETTER_TOPIC, Duration.ofSeconds(15));
            assertThat(record.value()).isEqualTo("{not json");
            assertThat(record.headers().lastHeader("kafka_dlt-exception-stacktrace")).isNotNull();
            assertThat(new String(record.headers().lastHeader("kafka_dlt-original-consumer-group").value())).isEqualTo("ms-report");
        }
    }
}
