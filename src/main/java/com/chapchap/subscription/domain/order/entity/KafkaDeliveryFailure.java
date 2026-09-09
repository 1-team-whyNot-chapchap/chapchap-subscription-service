package com.chapchap.subscription.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 16시 재시도까지 실패한 주문의 최종 Kafka 저장 실패 기록이다. */
@Getter
@Entity
@Table(name = "kafka_delivery_failures", uniqueConstraints = {
    @UniqueConstraint(name = "uk_kafka_delivery_failures_order", columnNames = "order_id"),
    @UniqueConstraint(name = "uk_kafka_delivery_failures_attempt", columnNames = "order_delivery_attempt_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KafkaDeliveryFailure {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED") private Long id;
    @Column(name = "order_id", nullable = false, columnDefinition = "BIGINT UNSIGNED") private Long orderId;
    @Column(name = "order_delivery_attempt_id", nullable = false, columnDefinition = "BIGINT UNSIGNED") private Long orderDeliveryAttemptId;
    @Column(name = "failure_code", nullable = false, length = 100) private String failureCode;
    @Column(name = "failure_reason", nullable = false, columnDefinition = "TEXT") private String failureReason;
    @Column(name = "failed_at", nullable = false, columnDefinition = "DATETIME(6)") private LocalDateTime failedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
        columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)") private LocalDateTime createdAt;

    public static KafkaDeliveryFailure create(OrderDeliveryAttempt attempt) {
        if (attempt.getId() == null || attempt.getAttemptSequence() != 2
            || attempt.getExecutionType() != OrderDeliveryAttemptExecutionType.RETRY_1600
            || attempt.getResult() != OrderDeliveryAttemptResult.FAILURE) {
            throw new IllegalArgumentException("Only a resolved retry failure can become final failure");
        }
        KafkaDeliveryFailure failure = new KafkaDeliveryFailure();
        failure.orderId = attempt.getOrderId();
        failure.orderDeliveryAttemptId = attempt.getId();
        failure.failureCode = attempt.getFailureCode();
        failure.failureReason = attempt.getFailureReason();
        failure.failedAt = attempt.getResolvedAt();
        return failure;
    }
}
