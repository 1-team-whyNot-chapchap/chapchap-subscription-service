package com.chapchap.subscription.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

/** Kafka Broker 저장 결과가 확정된 주문 전달 시도 이력이다. */
@Getter
@Entity
@Table(name = "order_delivery_attempts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_order_delivery_attempts_order_sequence", columnNames = {"order_id", "attempt_sequence"}),
    @UniqueConstraint(name = "uk_order_delivery_attempts_order_execution", columnNames = {"order_id", "execution_type"})
})
@Check(name = "ck_order_delivery_attempts_sequence", constraints = "attempt_sequence BETWEEN 1 AND 2")
@Check(name = "ck_order_delivery_attempts_execution", constraints = "(attempt_sequence = 1 AND execution_type = 'INITIAL_1500') OR (attempt_sequence = 2 AND execution_type = 'RETRY_1600')")
@Check(name = "ck_order_delivery_attempts_result", constraints = "(result = 'SUCCESS' AND kafka_topic IS NOT NULL AND kafka_partition IS NOT NULL AND kafka_offset IS NOT NULL AND failure_code IS NULL AND failure_reason IS NULL) OR (result = 'FAILURE' AND kafka_topic IS NULL AND kafka_partition IS NULL AND kafka_offset IS NULL AND failure_code IS NOT NULL AND failure_reason IS NOT NULL)")
@Check(name = "ck_order_delivery_attempts_resolved", constraints = "resolved_at >= attempted_at")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDeliveryAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "order_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long orderId;

    @Column(name = "attempt_sequence", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private int attemptSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_type", nullable = false, length = 20)
    private OrderDeliveryAttemptExecutionType executionType;

    @Column(name = "attempted_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private OrderDeliveryAttemptResult result;

    @Column(name = "kafka_topic", length = 255)
    private String kafkaTopic;

    @Column(name = "kafka_partition", columnDefinition = "INT UNSIGNED")
    private Integer kafkaPartition;

    @Column(name = "kafka_offset", columnDefinition = "BIGINT UNSIGNED")
    private Long kafkaOffset;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "resolved_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
        columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    public static OrderDeliveryAttempt success(
        Long orderId, int attemptSequence, OrderDeliveryAttemptExecutionType executionType,
        LocalDateTime attemptedAt, LocalDateTime resolvedAt, String topic, int partition, long offset
    ) {
        OrderDeliveryAttempt attempt = createBase(orderId, attemptSequence, executionType, attemptedAt, resolvedAt);
        attempt.result = OrderDeliveryAttemptResult.SUCCESS;
        attempt.kafkaTopic = requireText(topic, "topic");
        attempt.kafkaPartition = partition;
        attempt.kafkaOffset = offset;
        return attempt;
    }

    public static OrderDeliveryAttempt failure(
        Long orderId, int attemptSequence, OrderDeliveryAttemptExecutionType executionType,
        LocalDateTime attemptedAt, LocalDateTime resolvedAt, String failureCode, String failureReason
    ) {
        OrderDeliveryAttempt attempt = createBase(orderId, attemptSequence, executionType, attemptedAt, resolvedAt);
        attempt.result = OrderDeliveryAttemptResult.FAILURE;
        attempt.failureCode = requireText(failureCode, "failureCode");
        attempt.failureReason = requireText(failureReason, "failureReason");
        return attempt;
    }

    private static OrderDeliveryAttempt createBase(
        Long orderId, int attemptSequence, OrderDeliveryAttemptExecutionType executionType,
        LocalDateTime attemptedAt, LocalDateTime resolvedAt
    ) {
        if (orderId == null || orderId <= 0 || attemptSequence < 1 || attemptSequence > 2
            || executionType == null || attemptedAt == null || resolvedAt == null || resolvedAt.isBefore(attemptedAt)) {
            throw new IllegalArgumentException("Invalid order delivery attempt");
        }
        if ((attemptSequence == 1) != (executionType == OrderDeliveryAttemptExecutionType.INITIAL_1500)) {
            throw new IllegalArgumentException("Attempt sequence and execution type must match");
        }
        OrderDeliveryAttempt attempt = new OrderDeliveryAttempt();
        attempt.orderId = orderId;
        attempt.attemptSequence = attemptSequence;
        attempt.executionType = executionType;
        attempt.attemptedAt = attemptedAt;
        attempt.resolvedAt = resolvedAt;
        return attempt;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
