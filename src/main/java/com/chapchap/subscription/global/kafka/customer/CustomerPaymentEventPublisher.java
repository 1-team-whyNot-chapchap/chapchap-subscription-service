package com.chapchap.subscription.global.kafka.customer;

import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class CustomerPaymentEventPublisher {
    private static final String CURRENCY = "KRW";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILURE_CODE = "PAYMENT_FAILED";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CustomerPaymentKafkaProperties properties;
    private final PaymentTransactionRepository payments;
    private final OrderRepository orders;

    public CustomerPaymentEventPublisher(
        KafkaTemplate<String, Object> kafkaTemplate,
        CustomerPaymentKafkaProperties properties,
        PaymentTransactionRepository payments,
        OrderRepository orders
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.payments = payments;
        this.orders = orders;
    }

    public void publishCompletedAfterCommit(Long paymentTransactionId, LocalDateTime occurredAt) {
        PaymentTransaction payment = find(paymentTransactionId);
        requireStatus(payment, PaymentTransactionStatus.SUCCESS);
        long discountAmount = payment.getTransactionType() == PaymentTransactionType.SETTING_CHANGE_PAYMENT
            ? 0L
            : orders.findAllBySubscriptionPeriodId(payment.getSubscriptionPeriodId()).stream()
                .mapToLong(order -> order.getDiscountAmount()).sum();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            eventId(PaymentCompletedEvent.EVENT_TYPE, payment),
            PaymentCompletedEvent.EVENT_TYPE,
            1,
            CustomerKafkaPublicationSupport.toKst(occurredAt),
            payment.getUserId(),
            new PaymentCompletedEvent.Data(
                payment.getPublicId(), payment.getPaymentStateVersion(), payment.getTransactionType().name(),
                COMPLETED, payment.getTransactionAmount(), discountAmount, CURRENCY,
                payment.getPeriodStartDate(), payment.getPeriodEndDate()
            )
        );
        sendAfterCommit(payment.getPublicId(), event);
    }

    public void publishRegularFailureAfterCommit(
        Long paymentTransactionId,
        LocalDateTime failedAt,
        boolean finalAttempt
    ) {
        PaymentTransaction payment = find(paymentTransactionId);
        requireType(payment, PaymentTransactionType.REGULAR_PAYMENT);
        Object data;
        if (finalAttempt) {
            requireStatus(payment, PaymentTransactionStatus.FAILED);
            data = new PaymentFailedEvent.FinalFailureData(
                payment.getPublicId(), payment.getPaymentStateVersion(), payment.getTransactionType().name(),
                payment.getStatus().name(), payment.getTransactionAmount(),
                CustomerKafkaPublicationSupport.toKst(failedAt), payment.getPeriodStartDate().minusDays(1), FAILURE_CODE
            );
        } else {
            requireStatus(payment, PaymentTransactionStatus.RETRY_WAITING);
            data = new PaymentFailedEvent.RetryWaitingData(
                payment.getPublicId(), payment.getPaymentStateVersion(), payment.getTransactionType().name(),
                payment.getStatus().name(), payment.getTransactionAmount(),
                CustomerKafkaPublicationSupport.toKst(failedAt),
                CustomerKafkaPublicationSupport.toKst(failedAt.toLocalDate().atTime(LocalTime.of(13, 0)))
            );
        }
        PaymentFailedEvent event = new PaymentFailedEvent(
            eventId(PaymentFailedEvent.EVENT_TYPE, payment), PaymentFailedEvent.EVENT_TYPE, 1,
            CustomerKafkaPublicationSupport.toKst(failedAt), payment.getUserId(), data
        );
        sendAfterCommit(payment.getPublicId(), event);
    }

    public void publishSettingChangeFailureAfterCommit(Long paymentTransactionId, LocalDateTime failedAt) {
        PaymentTransaction payment = find(paymentTransactionId);
        requireType(payment, PaymentTransactionType.SETTING_CHANGE_PAYMENT);
        requireStatus(payment, PaymentTransactionStatus.FAILED);
        PaymentFailedEvent event = new PaymentFailedEvent(
            eventId(PaymentFailedEvent.EVENT_TYPE, payment), PaymentFailedEvent.EVENT_TYPE, 1,
            CustomerKafkaPublicationSupport.toKst(failedAt), payment.getUserId(),
            new PaymentFailedEvent.SettingChangeFailureData(
                payment.getPublicId(), payment.getPaymentStateVersion(), payment.getTransactionType().name(),
                payment.getStatus().name(), payment.getTransactionAmount(),
                CustomerKafkaPublicationSupport.toKst(failedAt), payment.getSettingEffectiveDate(), FAILURE_CODE
            )
        );
        sendAfterCommit(payment.getPublicId(), event);
    }

    public void publishRetryStoppedAfterCommit(PaymentTransaction payment, LocalDateTime stoppedAt) {
        requireType(payment, PaymentTransactionType.REGULAR_PAYMENT);
        requireStatus(payment, PaymentTransactionStatus.RETRY_STOPPED);
        PaymentRetryStoppedEvent event = new PaymentRetryStoppedEvent(
            eventId(PaymentRetryStoppedEvent.EVENT_TYPE, payment), PaymentRetryStoppedEvent.EVENT_TYPE, 1,
            CustomerKafkaPublicationSupport.toKst(stoppedAt), payment.getUserId(),
            new PaymentRetryStoppedEvent.Data(
                payment.getPublicId(), payment.getPaymentStateVersion(), payment.getTransactionType().name(),
                payment.getStatus().name(), CustomerKafkaPublicationSupport.toKst(stoppedAt)
            )
        );
        sendAfterCommit(payment.getPublicId(), event);
    }

    private PaymentTransaction find(Long id) {
        return payments.findById(id).orElseThrow(() -> new IllegalStateException("Payment event transaction is missing"));
    }

    private void requireType(PaymentTransaction payment, PaymentTransactionType expected) {
        if (payment.getTransactionType() != expected) {
            throw new IllegalStateException("Payment event type does not match transaction");
        }
    }

    private void requireStatus(PaymentTransaction payment, PaymentTransactionStatus expected) {
        if (payment.getStatus() != expected) {
            throw new IllegalStateException("Payment event status does not match transaction");
        }
    }

    private String eventId(String eventType, PaymentTransaction payment) {
        return CustomerKafkaPublicationSupport.deterministicEventId(
            eventType, payment.getPublicId() + ":" + payment.getPaymentStateVersion()
        );
    }

    private void sendAfterCommit(String key, Object event) {
        CustomerKafkaPublicationSupport.afterCommit(() -> kafkaTemplate.send(properties.getTopic(), key, event));
    }
}
