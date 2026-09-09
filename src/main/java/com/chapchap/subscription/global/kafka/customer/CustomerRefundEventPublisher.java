package com.chapchap.subscription.global.kafka.customer;

import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CustomerRefundEventPublisher {
    private static final String CURRENCY = "KRW";
    private static final String FAILURE_CODE = "REFUND_FAILED";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CustomerRefundKafkaProperties properties;

    public CustomerRefundEventPublisher(
        KafkaTemplate<String, Object> kafkaTemplate,
        CustomerRefundKafkaProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publishTerminalAfterCommit(
        Refund refund,
        PaymentTransaction cancellation,
        LocalDateTime occurredAt
    ) {
        Long userId = cancellation.getUserId();
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            RefundCompletedEvent event = new RefundCompletedEvent(
                CustomerKafkaPublicationSupport.deterministicEventId(
                    RefundCompletedEvent.EVENT_TYPE, refund.getPublicId()
                ),
                RefundCompletedEvent.EVENT_TYPE,
                1,
                CustomerKafkaPublicationSupport.toKst(occurredAt),
                userId,
                new RefundCompletedEvent.Data(
                    refund.getPublicId(), refund.getRefundType().name(), refund.getSuccessfulRefundAmount(),
                    CURRENCY, CustomerKafkaPublicationSupport.toKst(refund.getCompletedAt())
                )
            );
            sendAfterCommit(refund.getPublicId(), event);
            return;
        }
        if (refund.getStatus() == RefundStatus.FAILED) {
            RefundFailedEvent event = new RefundFailedEvent(
                CustomerKafkaPublicationSupport.deterministicEventId(
                    RefundFailedEvent.EVENT_TYPE, refund.getPublicId() + ":" + cancellation.getPublicId()
                ),
                RefundFailedEvent.EVENT_TYPE,
                1,
                CustomerKafkaPublicationSupport.toKst(occurredAt),
                userId,
                new RefundFailedEvent.Data(
                    refund.getPublicId(), refund.getRefundType().name(), refund.getRefundAmount(),
                    CURRENCY, CustomerKafkaPublicationSupport.toKst(occurredAt), FAILURE_CODE
                )
            );
            sendAfterCommit(refund.getPublicId(), event);
        }
    }

    private void sendAfterCommit(String key, Object event) {
        CustomerKafkaPublicationSupport.afterCommit(() -> kafkaTemplate.send(properties.getTopic(), key, event));
    }
}
