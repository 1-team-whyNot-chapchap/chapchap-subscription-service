package com.chapchap.subscription.global.kafka.delivery;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.service.DeliveryRefundCommand;
import com.chapchap.subscription.domain.payment.service.DeliveryRefundService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 로컬 Docker Kafka가 있을 때만 Delivery 환불 Consumer와 DLT를 실제 검증한다. */
@SpringBootTest(properties = {
    "app.kafka.delivery-refund.group-id=subscription-service-delivery-refund-it-group"
})
@ActiveProfiles("local")
@EnabledIfSystemProperty(
    named = "chapchap.delivery-refund-kafka-integration.enabled",
    matches = "true"
)
class DeliveryRefundKafkaBrokerIntegrationTest {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Autowired
    private DeliveryRefundKafkaProperties properties;

    @MockitoBean
    private DeliveryRefundService service;

    @Test
    void 정상_Event를_실제_Broker에서_소비해_업무서비스에_전달한다() throws Exception {
        String deliveryId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        when(service.process(any())).thenReturn(RefundStatus.COMPLETED);

        publish(properties.getTopic(), deliveryId, event(deliveryId, orderId, "DELIVERY_FAILED"));

        var command = org.mockito.ArgumentCaptor.forClass(DeliveryRefundCommand.class);
        verify(service, timeout(10_000)).process(command.capture());
        assertThat(command.getValue()).isEqualTo(new DeliveryRefundCommand(deliveryId, orderId, 10L));
    }

    @Test
    void 계약위반_Event는_재시도_후_실제_DLT에_저장한다() throws Exception {
        String deliveryId = UUID.randomUUID().toString();
        try (KafkaConsumer<String, String> consumer = consumer()) {
            TopicPartition partition = new TopicPartition(properties.getDltTopic(), 0);
            consumer.assign(List.of(partition));
            long initialEnd = consumer.endOffsets(List.of(partition)).get(partition);
            consumer.seek(partition, initialEnd);

            publish(properties.getTopic(), deliveryId,
                event(deliveryId, UUID.randomUUID().toString(), "CUSTOMER_CHANGED_MIND"));

            ConsumerRecord<String, String> record = awaitRecord(consumer, deliveryId, Duration.ofSeconds(12));
            assertThat(record.topic()).isEqualTo(properties.getDltTopic());
            assertThat(record.key()).isEqualTo(deliveryId);
        }
    }

    private String event(String deliveryId, String orderId, String reasonCode) {
        return """
            {"eventId":"%s","eventType":"DELIVERY_REFUND_CONFIRMED","version":1,
             "occurredAt":"2026-09-06T18:00:00+09:00","userId":10,
             "data":{"deliveryId":"%s","orderId":"%s",
             "confirmedAt":"2026-09-06T17:59:00+09:00","reasonCode":"%s"}}
            """.formatted(UUID.randomUUID(), deliveryId, orderId, reasonCode);
    }

    private void publish(String topic, String key, String value) throws Exception {
        Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        }
    }

    private KafkaConsumer<String, String> consumer() {
        Properties consumerProperties = new Properties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "delivery-refund-dlt-test-" + UUID.randomUUID());
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new KafkaConsumer<>(consumerProperties);
    }

    private ConsumerRecord<String, String> awaitRecord(
        KafkaConsumer<String, String> consumer,
        String key,
        Duration timeout
    ) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (key.equals(record.key())) return record;
            }
        }
        throw new AssertionError("Delivery refund event was not stored in DLT within " + timeout);
    }
}
