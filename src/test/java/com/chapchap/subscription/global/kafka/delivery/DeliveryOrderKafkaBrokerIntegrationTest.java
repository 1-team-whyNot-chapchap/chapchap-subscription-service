package com.chapchap.subscription.global.kafka.delivery;

import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryAttempt;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.repository.KafkaDeliveryFailureRepository;
import com.chapchap.subscription.domain.order.repository.OrderDeliveryAttemptRepository;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.repository.UserTermsAgreementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Docker Desktop의 로컬 Kafka Broker가 있을 때만 수행하는 실제 Producer 검증이다. */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "CHAPCHAP_KAFKA_INTEGRATION_ENABLED", matches = "true")
class DeliveryOrderKafkaBrokerIntegrationTest {
    private static final String TOPIC = "msa4-team1.subscription.delivery-orders.v1";

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderDeliveryAttemptRepository attemptRepository;
    @Autowired private KafkaDeliveryFailureRepository failureRepository;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private DeliveryOrderKafkaProperties properties;
    @Autowired private EntityManager entityManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    void 실제_Broker에_익일_유효_주문을_발행하고_DB_성공_이력을_남긴다() throws Exception {
        LocalDate executionDate = LocalDate.of(2026, 9, 7);
        Order order = order(executionDate.plusDays(1));
        orderRepository.saveAndFlush(order);

        MenuRepository menus = mock(MenuRepository.class);
        Menu menu = mock(Menu.class);
        when(menu.getPublicId()).thenReturn("00000000-0000-4000-8000-000000000001");
        when(menus.findById(7L)).thenReturn(Optional.of(menu));
        UserTermsAgreementRepository agreements = mock(UserTermsAgreementRepository.class);
        when(agreements.findById(5L)).thenReturn(Optional.of(
            UserTermsAgreement.create(10L, 3L, LocalDateTime.of(2026, 9, 1, 10, 0))
        ));
        DeliveryOrderPublisherService publisher = new DeliveryOrderPublisherService(
            orderRepository, attemptRepository, failureRepository, holidayRepository, menus, agreements,
            kafkaTemplate, properties, new KstReferenceTimeProvider()
        );

        try (KafkaConsumer<String, String> consumer = consumer()) {
            TopicPartition topicPartition = new TopicPartition(TOPIC, 0);
            consumer.assign(List.of(topicPartition));
            long initialEndOffset = consumer.endOffsets(List.of(topicPartition)).get(topicPartition);
            consumer.seek(topicPartition, initialEndOffset);

            publisher.publishInitialOrders(executionDate);
            orderRepository.flush();
            entityManager.refresh(order);

            ConsumerRecord<String, String> record = awaitRecord(consumer, order.getPublicId());
            JsonNode event = objectMapper.readTree(record.value());
            List<OrderDeliveryAttempt> attempts = entityManager.createQuery(
                "select attempt from OrderDeliveryAttempt attempt where attempt.orderId = :orderId",
                OrderDeliveryAttempt.class
            ).setParameter("orderId", order.getId()).getResultList();

            assertThat(record.key()).isEqualTo(order.getPublicId());
            assertThat(event.path("eventType").asText()).isEqualTo("SUBSCRIPTION_DELIVERY_ORDER_READY");
            assertThat(event.path("occurredAt").isTextual()).isTrue();
            assertThat(event.path("occurredAt").asText()).endsWith("+09:00");
            assertThat(event.path("data").path("orderId").asText()).isEqualTo(order.getPublicId());
            assertThat(event.path("data").path("deliveryDate").isTextual()).isTrue();
            assertThat(event.path("data").path("deliveryDate").asText()).isEqualTo("2026-09-08");
            assertThat(event.path("data").path("termsAgreedAt").isTextual()).isTrue();
            assertThat(event.path("data").path("termsAgreedAt").asText())
                .isEqualTo("2026-09-01T10:00:00+09:00");
            assertThat(event.path("data").path("deliveryAreaCode").isMissingNode()).isTrue();
            assertThat(event.path("data").path("entranceInformation").asText()).isEqualTo("7003");
            assertThat(order.getKafkaDeliveryStatus()).isEqualTo(OrderKafkaDeliveryStatus.COMPLETED);
            assertThat(order.getKafkaStoredAt()).isNotNull();
            assertThat(attempts).singleElement().satisfies(attempt -> {
                assertThat(attempt.getKafkaTopic()).isEqualTo(TOPIC);
                assertThat(attempt.getKafkaPartition()).isZero();
                assertThat(attempt.getKafkaOffset()).isNotNegative();
            });

            publisher.publishInitialOrders(executionDate);
            orderRepository.flush();
            assertNoRecord(consumer, order.getPublicId());
            assertThat(attempts(order)).hasSize(1);
        }
    }

    @Test
    void 비활성_주문과_일요일_배송_주문은_실제_Broker에_발행하지_않는다() {
        Order inactiveOrder = awaitingOrder(LocalDate.of(2026, 9, 8));
        Order sundayOrder = order(LocalDate.of(2026, 9, 6));
        orderRepository.saveAndFlush(inactiveOrder);
        orderRepository.saveAndFlush(sundayOrder);

        try (KafkaConsumer<String, String> consumer = consumer()) {
            TopicPartition topicPartition = new TopicPartition(TOPIC, 0);
            consumer.assign(List.of(topicPartition));
            long initialEndOffset = consumer.endOffsets(List.of(topicPartition)).get(topicPartition);
            consumer.seek(topicPartition, initialEndOffset);

            publisher(kafkaTemplate).publishInitialOrders(LocalDate.of(2026, 9, 7));
            publisher(kafkaTemplate).publishInitialOrders(LocalDate.of(2026, 9, 5));
            orderRepository.flush();
            entityManager.refresh(inactiveOrder);
            entityManager.refresh(sundayOrder);

            assertNoRecord(consumer, inactiveOrder.getPublicId());
            assertNoRecord(consumer, sundayOrder.getPublicId());
        }

        assertThat(inactiveOrder.getKafkaDeliveryStatus()).isEqualTo(OrderKafkaDeliveryStatus.NOT_SENT);
        assertThat(sundayOrder.getKafkaDeliveryStatus()).isEqualTo(OrderKafkaDeliveryStatus.NOT_SENT);
        assertThat(attempts(inactiveOrder)).isEmpty();
        assertThat(attempts(sundayOrder)).isEmpty();
    }

    @Test
    void Broker_미가용_초기_실패_뒤_실제_Broker_재시도로_완료한다() throws Exception {
        LocalDate executionDate = LocalDate.of(2026, 9, 7);
        Order order = order(executionDate.plusDays(1));
        orderRepository.saveAndFlush(order);

        DefaultKafkaProducerFactory<String, Object> unavailableFactory = unavailableProducerFactory();
        try {
            publisher(new KafkaTemplate<>(unavailableFactory), timeProvider(executionDate))
                .publishInitialOrders(executionDate);
        } finally {
            unavailableFactory.destroy();
        }
        orderRepository.flush();
        entityManager.refresh(order);
        assertThat(order.getKafkaDeliveryStatus()).isEqualTo(OrderKafkaDeliveryStatus.FAILED);
        assertThat(attempts(order)).singleElement().satisfies(attempt -> {
            assertThat(attempt.getAttemptSequence()).isEqualTo(1);
            assertThat(attempt.getFailureCode()).isIn("KAFKA_TIMEOUT", "KAFKA_SEND_FAILED");
        });

        try (KafkaConsumer<String, String> consumer = consumer()) {
            TopicPartition topicPartition = new TopicPartition(TOPIC, 0);
            consumer.assign(List.of(topicPartition));
            long initialEndOffset = consumer.endOffsets(List.of(topicPartition)).get(topicPartition);
            consumer.seek(topicPartition, initialEndOffset);

            publisher(kafkaTemplate, timeProvider(executionDate)).publishFailedOrdersOnce(executionDate);
            orderRepository.flush();
            entityManager.refresh(order);

            assertThat(awaitRecord(consumer, order.getPublicId()).key()).isEqualTo(order.getPublicId());
        }

        assertThat(order.getKafkaDeliveryStatus()).isEqualTo(OrderKafkaDeliveryStatus.COMPLETED);
        assertThat(attempts(order)).satisfiesExactly(
            initial -> {
                assertThat(initial.getAttemptSequence()).isEqualTo(1);
                assertThat(initial.getFailureCode()).isNotBlank();
            },
            retry -> {
                assertThat(retry.getAttemptSequence()).isEqualTo(2);
                assertThat(retry.getKafkaTopic()).isEqualTo(TOPIC);
                assertThat(retry.getKafkaOffset()).isNotNegative();
            }
        );
    }

    @Test
    void Broker_미가용_재시도까지_실패하면_최종_실패_이력을_남긴다() {
        LocalDate executionDate = LocalDate.of(2026, 9, 7);
        Order order = order(executionDate.plusDays(1));
        orderRepository.saveAndFlush(order);

        DefaultKafkaProducerFactory<String, Object> unavailableFactory = unavailableProducerFactory();
        try {
            KafkaTemplate<String, Object> unavailableTemplate = new KafkaTemplate<>(unavailableFactory);
            DeliveryOrderPublisherService publisher = publisher(unavailableTemplate, timeProvider(executionDate));
            publisher.publishInitialOrders(executionDate);
            publisher.publishFailedOrdersOnce(executionDate);
        } finally {
            unavailableFactory.destroy();
        }
        orderRepository.flush();
        entityManager.refresh(order);

        Long finalFailureCount = entityManager.createQuery(
            "select count(failure) from KafkaDeliveryFailure failure where failure.orderId = :orderId", Long.class
        ).setParameter("orderId", order.getId()).getSingleResult();
        assertThat(order.getKafkaDeliveryStatus()).isEqualTo(OrderKafkaDeliveryStatus.FINAL_FAILED);
        assertThat(attempts(order)).hasSize(2).allSatisfy(attempt -> {
            assertThat(attempt.getFailureCode()).isIn("KAFKA_TIMEOUT", "KAFKA_SEND_FAILED");
        });
        assertThat(finalFailureCount).isEqualTo(1L);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CHAPCHAP_KAFKA_BROKER_STOPPED_TEST_ENABLED", matches = "true")
    void 실제_Docker_Broker가_중지되면_초기_실패_이력을_남긴다() {
        LocalDate executionDate = LocalDate.of(2026, 9, 7);
        Order order = order(executionDate.plusDays(1));
        orderRepository.saveAndFlush(order);

        publisher(kafkaTemplate, timeProvider(executionDate)).publishInitialOrders(executionDate);
        orderRepository.flush();
        entityManager.refresh(order);

        assertThat(order.getKafkaDeliveryStatus()).isEqualTo(OrderKafkaDeliveryStatus.FAILED);
        assertThat(attempts(order)).singleElement().satisfies(attempt -> {
            assertThat(attempt.getAttemptSequence()).isEqualTo(1);
            assertThat(attempt.getFailureCode()).isIn("KAFKA_TIMEOUT", "KAFKA_SEND_FAILED");
        });
    }

    private KafkaConsumer<String, String> consumer() {
        Properties consumerProperties = new Properties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "subscription-kafka-broker-test-" + UUID.randomUUID());
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new KafkaConsumer<>(consumerProperties);
    }

    private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer, String orderId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (orderId.equals(record.key())) return record;
            }
        }
        throw new AssertionError("Kafka Broker did not store the delivery order event within 10 seconds");
    }

    private void assertNoRecord(KafkaConsumer<String, String> consumer, String orderId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                assertThat(record.key()).isNotEqualTo(orderId);
            }
        }
    }

    private DeliveryOrderPublisherService publisher(KafkaTemplate<String, Object> template) {
        return publisher(template, new KstReferenceTimeProvider());
    }

    private DeliveryOrderPublisherService publisher(KafkaTemplate<String, Object> template,
                                                    KstReferenceTimeProvider timeProvider) {
        MenuRepository menus = mock(MenuRepository.class);
        Menu menu = mock(Menu.class);
        when(menu.getPublicId()).thenReturn("00000000-0000-4000-8000-000000000001");
        when(menus.findById(7L)).thenReturn(Optional.of(menu));
        UserTermsAgreementRepository agreements = mock(UserTermsAgreementRepository.class);
        when(agreements.findById(5L)).thenReturn(Optional.of(
            UserTermsAgreement.create(10L, 3L, LocalDateTime.of(2026, 9, 1, 10, 0))
        ));
        return new DeliveryOrderPublisherService(
            orderRepository, attemptRepository, failureRepository, holidayRepository, menus, agreements,
            template, properties, timeProvider
        );
    }

    private KstReferenceTimeProvider timeProvider(LocalDate executionDate) {
        KstReferenceTimeProvider timeProvider = mock(KstReferenceTimeProvider.class);
        when(timeProvider.now()).thenReturn(executionDate.atTime(15, 0));
        return timeProvider;
    }

    private List<OrderDeliveryAttempt> attempts(Order order) {
        return entityManager.createQuery(
            "select attempt from OrderDeliveryAttempt attempt where attempt.orderId = :orderId order by attempt.attemptSequence",
            OrderDeliveryAttempt.class
        ).setParameter("orderId", order.getId()).getResultList();
    }

    private DefaultKafkaProducerFactory<String, Object> unavailableProducerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class,
            ProducerConfig.ACKS_CONFIG, "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 2_000,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1_000,
            ProducerConfig.MAX_BLOCK_MS_CONFIG, 1_000
        ));
    }

    private Order order(LocalDate deliveryDate) {
        Order order = awaitingOrder(deliveryDate);
        order.activateAfterPayment();
        return order;
    }

    private Order awaitingOrder(LocalDate deliveryDate) {
        return Order.createAwaitingConfirmation(
            10L, uniqueId(), uniqueId(), uniqueId(), 5L, uniqueId(), uniqueId(), 7L, deliveryDate, 1,
            "Kafka 통합 테스트 플랜", "Kafka 통합 테스트 메뉴", 8_900L, 2, 17_800L, 0L, 0L, 17_800L,
            "테스트 수령인", "010-0000-0000", "41911", "대구광역시 중구 테스트로 1", "101호",
            "DOORSTEP", null, "7003", OrderDeliveryTimeSlot.TIME_1100_1300
        );
    }

    private long uniqueId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }
}
