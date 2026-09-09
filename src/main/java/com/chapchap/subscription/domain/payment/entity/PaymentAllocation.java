package com.chapchap.subscription.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.GeneratedColumn;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 성공한 원 결제금액 중 특정 주문에 사용된 금액을 나타내는 배분 관계다.
 *
 * <p>한 주문과 한 원 결제 조합에는 하나만 존재한다. 취소 가능 금액은
 * 최초 배분금액에서 누적 취소 배분금액을 뺀 DB 생성 컬럼이므로 애플리케이션이 직접 수정하지 않는다.</p>
 */
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "payment_allocations",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_allocations_order_transaction", columnNames = {"order_id", "original_payment_transaction_id"})
    },
    indexes = {
        @Index(name = "idx_payment_allocations_transaction", columnList = "original_payment_transaction_id, id")
    }
)
@Check(name = "ck_payment_allocations_amount", constraints = "allocated_amount >= 1")
@Check(
    name = "ck_payment_allocations_cancelled_amount",
    constraints = "cumulative_cancelled_amount >= 0 AND cumulative_cancelled_amount <= allocated_amount"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "order_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long orderId;

    @Column(name = "original_payment_transaction_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long originalPaymentTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false, length = 40)
    private PaymentAllocationType allocationType;

    @Column(name = "allocated_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long allocatedAmount;

    @Column(name = "cumulative_cancelled_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long cumulativeCancelledAmount;

    @GeneratedColumn("allocated_amount - cumulative_cancelled_amount")
    @Column(name = "cancelable_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long cancelableAmount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    private PaymentAllocation(
        Long orderId,
        Long originalPaymentTransactionId,
        PaymentAllocationType allocationType,
        Long allocatedAmount
    ) {
        this.orderId = requirePositive(orderId, "orderId");
        this.originalPaymentTransactionId = requirePositive(originalPaymentTransactionId, "originalPaymentTransactionId");
        this.allocationType = requireNonNull(allocationType, "allocationType");
        this.allocatedAmount = requirePositive(allocatedAmount, "allocatedAmount");
        this.cumulativeCancelledAmount = 0L;
    }

    /**
     * 성공한 원 결제와 주문 사이의 최초 금액 배분을 생성한다.
     *
     * <p>누적 취소 배분금액은 0으로 시작한다. 연결한 거래의 성공 여부,
     * 거래 구분 일치 여부와 전체 배분 합계는 여러 데이터를 함께 확인하는 Service에서 검증한다.</p>
     *
     * @param orderId 결제금액을 사용하는 주문의 내부 식별자
     * @param originalPaymentTransactionId 금액을 제공한 성공 원 결제 거래 식별자
     * @param allocationType 원 결제 거래 구분을 보존한 배분 구분
     * @param allocatedAmount 원 결제금액 중 주문에 최초 배분한 금액
     * @return 누적 취소 배분금액이 0인 최초 배분 관계
     * @throws IllegalArgumentException 식별자·배분 구분·배분금액이 유효하지 않은 경우
     */
    public static PaymentAllocation create(
        Long orderId,
        Long originalPaymentTransactionId,
        PaymentAllocationType allocationType,
        Long allocatedAmount
    ) {
        return new PaymentAllocation(orderId, originalPaymentTransactionId, allocationType, allocatedAmount);
    }

    public void cancel(long amount) {
        long currentCancelable = currentCancelableAmount();
        if (amount <= 0 || amount > currentCancelable) {
            throw new IllegalArgumentException("Cancellation amount exceeds allocation balance");
        }
        cumulativeCancelledAmount = Math.addExact(cumulativeCancelledAmount, amount);
    }

    public long currentCancelableAmount() {
        return allocatedAmount - cumulativeCancelledAmount;
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
