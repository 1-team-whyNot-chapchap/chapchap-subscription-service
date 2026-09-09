package com.chapchap.subscription.global.kafka.customer;

import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.repository.PlanRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.global.kafka.auth.AuthSubscriptionStatus;
import com.chapchap.subscription.global.kafka.auth.SubscriptionStatusChangedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Docker Desktop의 로컬 Kafka Broker가 있을 때만 수행하는 Customer Producer 직렬화 검증이다. */
@EnabledIfEnvironmentVariable(named = "CHAPCHAP_CUSTOMER_KAFKA_INTEGRATION_ENABLED", matches = "true")
class CustomerKafkaBrokerIntegrationTest {
    private static final String PAYMENT_TOPIC = "msa4-team1.subscription.payment-events.v1";
    private static final String REFUND_TOPIC = "msa4-team1.subscription.refund-events.v1";
    private static final String NOTIFICATION_TOPIC = "msa4-team1.subscription.customer-notification-events.v1";
    private static final String AUTH_TOPIC = "msa4-team1.subscription.subscription-events.v1";
    private static final String ADDRESS_TOPIC = "msa4-team1.subscription.delivery-address-events.v1";
    private final String bootstrapServers = System.getenv().getOrDefault(
        "KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"
    );
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void 모든_Producer_영역의_대표_Event를_Source_날짜시각_문자열로_저장한다() throws Exception {
        DefaultKafkaProducerFactory<String, Object> factory = producerFactory();
        try {
            KafkaTemplate<String, Object> template = new KafkaTemplate<>(factory);
            verifyPaymentTopic(template);
            verifyPaymentFailureTopic(template);
            verifyRefundTopic(template);
            verifyNotificationTopic(template);
            verifySettingChangedTopic(template);
            verifyAuthTopic(template);
            verifyAddressTopic(template);
        } finally {
            factory.destroy();
        }
    }

    private void verifyPaymentFailureTopic(KafkaTemplate<String, Object> template) throws Exception {
        String paymentId = UUID.randomUUID().toString();
        OffsetDateTime failedAt = OffsetDateTime.parse("2026-09-06T09:00:00+09:00");
        PaymentFailedEvent event = new PaymentFailedEvent(
            UUID.randomUUID().toString(), PaymentFailedEvent.EVENT_TYPE, 1, failedAt, 25L,
            new PaymentFailedEvent.RetryWaitingData(
                paymentId, 2L, "REGULAR_PAYMENT", "RETRY_WAITING", 12_900L, failedAt,
                OffsetDateTime.parse("2026-09-06T13:00:00+09:00")
            )
        );

        try (KafkaConsumer<String, String> consumer = consumer(PAYMENT_TOPIC)) {
            template.send(PAYMENT_TOPIC, paymentId, event);
            JsonNode stored = objectMapper.readTree(awaitRecord(consumer, paymentId).value());
            assertThat(stored.path("occurredAt").asText()).isEqualTo("2026-09-06T09:00:00+09:00");
            assertThat(stored.path("data").path("failedAt").asText())
                .isEqualTo("2026-09-06T09:00:00+09:00");
            assertThat(stored.path("data").path("retryScheduledAt").asText())
                .isEqualTo("2026-09-06T13:00:00+09:00");
        }
    }

    private void verifyPaymentTopic(KafkaTemplate<String, Object> template) throws Exception {
        PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        PaymentTransaction payment = mock(PaymentTransaction.class);
        Order order = mock(Order.class);
        String paymentId = UUID.randomUUID().toString();
        when(payments.findById(1L)).thenReturn(Optional.of(payment));
        when(payment.getPublicId()).thenReturn(paymentId);
        when(payment.getUserId()).thenReturn(25L);
        when(payment.getPaymentStateVersion()).thenReturn(1L);
        when(payment.getTransactionType()).thenReturn(PaymentTransactionType.REGULAR_PAYMENT);
        when(payment.getStatus()).thenReturn(PaymentTransactionStatus.SUCCESS);
        when(payment.getTransactionAmount()).thenReturn(12_900L);
        when(payment.getSubscriptionPeriodId()).thenReturn(2L);
        when(payment.getPeriodStartDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(payment.getPeriodEndDate()).thenReturn(LocalDate.of(2026, 10, 4));
        when(orders.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of(order));
        when(order.getDiscountAmount()).thenReturn(1_000L);
        CustomerPaymentKafkaProperties properties = new CustomerPaymentKafkaProperties();
        properties.setTopic(PAYMENT_TOPIC);

        try (KafkaConsumer<String, String> consumer = consumer(PAYMENT_TOPIC)) {
            new CustomerPaymentEventPublisher(template, properties, payments, orders)
                .publishCompletedAfterCommit(1L, LocalDateTime.of(2026, 9, 6, 22, 0));
            JsonNode event = objectMapper.readTree(awaitRecord(consumer, paymentId).value());
            assertThat(event.path("eventType").asText()).isEqualTo("PAYMENT_COMPLETED");
            assertThat(event.path("occurredAt").isTextual()).isTrue();
            assertThat(event.path("occurredAt").asText()).isEqualTo("2026-09-06T22:00:00+09:00");
            assertThat(event.path("data").path("periodStartDate").isTextual()).isTrue();
            assertThat(event.path("data").path("periodStartDate").asText()).isEqualTo("2026-09-07");
            assertThat(event.path("data").path("periodEndDate").isTextual()).isTrue();
            assertThat(event.path("data").path("periodEndDate").asText()).isEqualTo("2026-10-04");
            assertThat(event.path("data").path("discountAmount").asLong()).isEqualTo(1_000L);
        }
    }

    private void verifyRefundTopic(KafkaTemplate<String, Object> template) throws Exception {
        Refund refund = mock(Refund.class);
        PaymentTransaction cancellation = mock(PaymentTransaction.class);
        String refundId = UUID.randomUUID().toString();
        LocalDateTime completedAt = LocalDateTime.of(2026, 9, 6, 22, 1);
        when(refund.getPublicId()).thenReturn(refundId);
        when(refund.getRefundType()).thenReturn(RefundType.DELIVERY_PARTIAL_CANCELLATION);
        when(refund.getStatus()).thenReturn(RefundStatus.COMPLETED);
        when(refund.getSuccessfulRefundAmount()).thenReturn(8_900L);
        when(refund.getCompletedAt()).thenReturn(completedAt);
        when(cancellation.getUserId()).thenReturn(25L);
        CustomerRefundKafkaProperties properties = new CustomerRefundKafkaProperties();
        properties.setTopic(REFUND_TOPIC);

        try (KafkaConsumer<String, String> consumer = consumer(REFUND_TOPIC)) {
            new CustomerRefundEventPublisher(template, properties)
                .publishTerminalAfterCommit(refund, cancellation, completedAt);
            JsonNode event = objectMapper.readTree(awaitRecord(consumer, refundId).value());
            assertThat(event.path("eventType").asText()).isEqualTo("REFUND_COMPLETED");
            assertThat(event.path("occurredAt").asText()).isEqualTo("2026-09-06T22:01:00+09:00");
            assertThat(event.path("data").path("completedAt").asText())
                .isEqualTo("2026-09-06T22:01:00+09:00");
            assertThat(event.path("data").path("refundedAmount").asLong()).isEqualTo(8_900L);
        }
    }

    private void verifyNotificationTopic(KafkaTemplate<String, Object> template) throws Exception {
        Subscription subscription = mock(Subscription.class);
        String subscriptionId = UUID.randomUUID().toString();
        when(subscription.getPublicId()).thenReturn(subscriptionId);
        when(subscription.getUserId()).thenReturn(25L);
        CustomerSubscriptionNotificationKafkaProperties properties =
            new CustomerSubscriptionNotificationKafkaProperties();
        properties.setTopic(NOTIFICATION_TOPIC);
        CustomerSubscriptionNotificationPublisher publisher = new CustomerSubscriptionNotificationPublisher(
            template, properties, mock(SubscriptionRepository.class), mock(SubscriptionPeriodRepository.class),
            mock(PlanRepository.class), mock(SubscriptionDeliveryConditionRepository.class),
            mock(AddressRepository.class)
        );

        try (KafkaConsumer<String, String> consumer = consumer(NOTIFICATION_TOPIC)) {
            publisher.publishEndedAfterCommit(
                subscription, "CUSTOMER_CANCELLATION", LocalDateTime.of(2026, 9, 6, 22, 2)
            );
            JsonNode event = objectMapper.readTree(awaitRecord(consumer, "25").value());
            assertThat(event.path("eventType").asText()).isEqualTo("SUBSCRIPTION_ENDED");
            assertThat(event.path("occurredAt").asText()).isEqualTo("2026-09-06T22:02:00+09:00");
            assertThat(event.path("data").path("endedAt").asText())
                .isEqualTo("2026-09-06T22:02:00+09:00");
            assertThat(event.path("data").path("endReason").asText()).isEqualTo("CUSTOMER_CANCELLATION");
        }
    }

    private void verifySettingChangedTopic(KafkaTemplate<String, Object> template) throws Exception {
        String key = UUID.randomUUID().toString();
        SubscriptionSettingChangedEvent event = new SubscriptionSettingChangedEvent(
            UUID.randomUUID().toString(), SubscriptionSettingChangedEvent.EVENT_TYPE, 1,
            OffsetDateTime.parse("2026-09-06T22:03:00+09:00"), 25L,
            new SubscriptionSettingChangedEvent.Data(2, LocalDate.parse("2026-09-08"), "가정식", List.of())
        );

        try (KafkaConsumer<String, String> consumer = consumer(NOTIFICATION_TOPIC)) {
            template.send(NOTIFICATION_TOPIC, key, event);
            JsonNode stored = objectMapper.readTree(awaitRecord(consumer, key).value());
            assertThat(stored.path("occurredAt").asText()).isEqualTo("2026-09-06T22:03:00+09:00");
            assertThat(stored.path("data").path("effectiveDate").isTextual()).isTrue();
            assertThat(stored.path("data").path("effectiveDate").asText()).isEqualTo("2026-09-08");
        }
    }

    private void verifyAuthTopic(KafkaTemplate<String, Object> template) throws Exception {
        String key = UUID.randomUUID().toString();
        SubscriptionStatusChangedEvent event = new SubscriptionStatusChangedEvent(
            UUID.randomUUID().toString(), SubscriptionStatusChangedEvent.EVENT_TYPE, 1,
            OffsetDateTime.parse("2026-09-06T22:04:00+09:00"), 25L,
            new SubscriptionStatusChangedEvent.Data(AuthSubscriptionStatus.ACTIVE, 2)
        );

        try (KafkaConsumer<String, String> consumer = consumer(AUTH_TOPIC)) {
            template.send(AUTH_TOPIC, key, event);
            JsonNode stored = objectMapper.readTree(awaitRecord(consumer, key).value());
            assertThat(stored.path("occurredAt").isTextual()).isTrue();
            assertThat(stored.path("occurredAt").asText()).isEqualTo("2026-09-06T22:04:00+09:00");
        }
    }

    private void verifyAddressTopic(KafkaTemplate<String, Object> template) throws Exception {
        String key = UUID.randomUUID().toString();
        DeliveryAddressChangedEvent event = new DeliveryAddressChangedEvent(
            UUID.randomUUID().toString(), DeliveryAddressChangedEvent.EVENT_TYPE, 1,
            OffsetDateTime.parse("2026-09-06T22:05:00+09:00"), 25L,
            new DeliveryAddressChangedEvent.Data(UUID.randomUUID().toString(), 2L, "회사")
        );

        try (KafkaConsumer<String, String> consumer = consumer(ADDRESS_TOPIC)) {
            template.send(ADDRESS_TOPIC, key, event);
            JsonNode stored = objectMapper.readTree(awaitRecord(consumer, key).value());
            assertThat(stored.path("occurredAt").isTextual()).isTrue();
            assertThat(stored.path("occurredAt").asText()).isEqualTo("2026-09-06T22:05:00+09:00");
        }
    }

    private DefaultKafkaProducerFactory<String, Object> producerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class,
            ProducerConfig.ACKS_CONFIG, "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true
        ));
    }

    private KafkaConsumer<String, String> consumer(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "customer-producer-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        TopicPartition partition = new TopicPartition(topic, 0);
        consumer.assign(List.of(partition));
        long end = consumer.endOffsets(List.of(partition)).get(partition);
        consumer.seek(partition, end);
        return consumer;
    }

    private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer, String key) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("Kafka record was not received for key " + key);
    }
}
