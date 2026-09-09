package com.chapchap.subscription.global.kafka.delivery;

import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.KafkaDeliveryFailure;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryAttempt;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryAttemptExecutionType;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryAttemptResult;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.KafkaDeliveryFailureRepository;
import com.chapchap.subscription.domain.order.repository.OrderDeliveryAttemptRepository;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.repository.UserTermsAgreementRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 정해진 시각에 다음 날 유효 주문을 Kafka Broker에 저장하고 결과 이력을 남긴다. */
@Service
public class DeliveryOrderPublisherService {
    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);

    private final OrderRepository orderRepository;
    private final OrderDeliveryAttemptRepository attemptRepository;
    private final KafkaDeliveryFailureRepository failureRepository;
    private final HolidayRepository holidayRepository;
    private final MenuRepository menuRepository;
    private final UserTermsAgreementRepository termsAgreementRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DeliveryOrderKafkaProperties properties;
    private final KstReferenceTimeProvider timeProvider;

    public DeliveryOrderPublisherService(
        OrderRepository orderRepository, OrderDeliveryAttemptRepository attemptRepository,
        KafkaDeliveryFailureRepository failureRepository, HolidayRepository holidayRepository,
        MenuRepository menuRepository, UserTermsAgreementRepository termsAgreementRepository,
        KafkaTemplate<String, Object> kafkaTemplate, DeliveryOrderKafkaProperties properties,
        KstReferenceTimeProvider timeProvider
    ) {
        this.orderRepository = orderRepository;
        this.attemptRepository = attemptRepository;
        this.failureRepository = failureRepository;
        this.holidayRepository = holidayRepository;
        this.menuRepository = menuRepository;
        this.termsAgreementRepository = termsAgreementRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public void publishInitialOrders(LocalDate executionDate) {
        LocalDate deliveryDate = executionDate.plusDays(1);
        if (isNonDeliveryDay(deliveryDate)) return;
        orders(deliveryDate, OrderKafkaDeliveryStatus.NOT_SENT).forEach(order ->
            publish(order, 1, OrderDeliveryAttemptExecutionType.INITIAL_1500, false)
        );
    }

    @Transactional
    public void publishFailedOrdersOnce(LocalDate executionDate) {
        LocalDate deliveryDate = executionDate.plusDays(1);
        if (isNonDeliveryDay(deliveryDate)) return;
        LocalDateTime start = executionDate.atStartOfDay();
        LocalDateTime end = executionDate.plusDays(1).atStartOfDay();
        orders(deliveryDate, OrderKafkaDeliveryStatus.FAILED).stream()
            .filter(order -> attemptRepository.existsByOrderIdAndAttemptSequenceAndResultAndAttemptedAtBetween(
                order.getId(), 1, OrderDeliveryAttemptResult.FAILURE, start, end
            ))
            .forEach(order -> publish(order, 2, OrderDeliveryAttemptExecutionType.RETRY_1600, true));
    }

    private List<Order> orders(LocalDate deliveryDate, OrderKafkaDeliveryStatus kafkaStatus) {
        return orderRepository.findAllByDeliveryDateAndStatusAndKafkaDeliveryStatus(
            deliveryDate, OrderStatus.ACTIVE, kafkaStatus
        );
    }

    private boolean isNonDeliveryDay(LocalDate deliveryDate) {
        return deliveryDate.getDayOfWeek() == DayOfWeek.SUNDAY
            || holidayRepository.existsByHolidayDateIn(List.of(deliveryDate));
    }

    private void publish(Order order, int sequence, OrderDeliveryAttemptExecutionType executionType, boolean finalAttempt) {
        LocalDateTime attemptedAt = timeProvider.now();
        try {
            SubscriptionDeliveryOrderReadyEvent event = eventOf(order, attemptedAt);
            SendResult<String, Object> result = kafkaTemplate.send(properties.getTopic(), order.getPublicId(), event)
                .get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            RecordMetadata metadata = result.getRecordMetadata();
            LocalDateTime resolvedAt = timeProvider.now();
            attemptRepository.save(OrderDeliveryAttempt.success(
                order.getId(), sequence, executionType, attemptedAt, resolvedAt,
                metadata.topic(), metadata.partition(), metadata.offset()
            ));
            order.markKafkaDeliveryCompleted(resolvedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(order, sequence, executionType, attemptedAt, "KAFKA_INTERRUPTED", "Kafka send was interrupted", finalAttempt);
        } catch (TimeoutException exception) {
            recordFailure(order, sequence, executionType, attemptedAt, "KAFKA_TIMEOUT", "Kafka broker acknowledgement timed out", finalAttempt);
        } catch (ExecutionException exception) {
            recordFailure(order, sequence, executionType, attemptedAt, "KAFKA_SEND_FAILED", exception.getCause().getClass().getSimpleName(), finalAttempt);
        } catch (RuntimeException exception) {
            recordFailure(order, sequence, executionType, attemptedAt, "KAFKA_SEND_FAILED", exception.getClass().getSimpleName(), finalAttempt);
        }
    }

    private void recordFailure(Order order, int sequence, OrderDeliveryAttemptExecutionType executionType,
                               LocalDateTime attemptedAt, String code, String reason, boolean finalAttempt) {
        OrderDeliveryAttempt attempt = attemptRepository.saveAndFlush(OrderDeliveryAttempt.failure(
            order.getId(), sequence, executionType, attemptedAt, timeProvider.now(), code, reason
        ));
        if (finalAttempt) {
            order.markKafkaDeliveryFinalFailed();
            failureRepository.save(KafkaDeliveryFailure.create(attempt));
        } else {
            order.markKafkaDeliveryFailed();
        }
    }

    private SubscriptionDeliveryOrderReadyEvent eventOf(Order order, LocalDateTime occurredAt) {
        if (order.getUserId() == null || order.getUserId() <= 0) throw new IllegalStateException("Invalid event userId");
        Menu menu = menuRepository.findById(order.getMenuId()).orElseThrow(() -> new IllegalStateException("Menu not found"));
        UserTermsAgreement agreement = termsAgreementRepository.findById(order.getNonFaceToFaceTermsAgreementId())
            .orElseThrow(() -> new IllegalStateException("Terms agreement not found"));
        if (!order.getUserId().equals(agreement.getUserId())) throw new IllegalStateException("Terms agreement owner mismatch");
        OffsetDateTime occurredAtOffset = occurredAt.atOffset(KST_OFFSET);
        return new SubscriptionDeliveryOrderReadyEvent(
            stableEventId(order.getPublicId()), SubscriptionDeliveryOrderReadyEvent.EVENT_TYPE, 1, occurredAtOffset,
            order.getUserId(), new SubscriptionDeliveryOrderReadyEvent.Data(
                order.getPublicId(), order.getDeliveryDate(), slotOf(order), order.getMealQuantity(),
                order.getRecipientName(), order.getRecipientPhone(), order.getPostalCode(), order.getAddressLine1(),
                order.getAddressLine2(), order.getDeliveryMethodCode(), order.getOtherDeliveryRequest(),
                order.getEntrancePassword(), true, agreement.getAgreedAt().atOffset(KST_OFFSET),
                List.of(new SubscriptionDeliveryOrderReadyEvent.MenuItem(menu.getPublicId(), order.getMenuName(), order.getMealQuantity()))
            )
        );
    }

    private String stableEventId(String orderId) {
        return UUID.nameUUIDFromBytes((SubscriptionDeliveryOrderReadyEvent.EVENT_TYPE + ":" + orderId)
            .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String slotOf(Order order) {
        return switch (order.getDeliveryTimeSlot()) {
            case TIME_1100_1300 -> "LUNCH";
            case TIME_1700_1900 -> "DINNER";
        };
    }
}
