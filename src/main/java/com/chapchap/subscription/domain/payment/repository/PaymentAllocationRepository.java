package com.chapchap.subscription.domain.payment.repository;

import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 성공한 원 결제금액의 주문별 배분 관계를 저장하고 조회한다. */
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    /** 원 결제 거래 한 건의 주문별 금액 배분을 식별자 순서로 조회한다. */
    List<PaymentAllocation> findAllByOriginalPaymentTransactionIdOrderByIdAsc(Long originalPaymentTransactionId);

    /** 주문 한 건에 연결된 원 결제별 금액 배분을 식별자 순서로 조회한다. */
    List<PaymentAllocation> findAllByOrderIdOrderByIdAsc(Long orderId);

    List<PaymentAllocation> findAllByOrderIdInOrderByIdAsc(List<Long> orderIds);

    void deleteAllByOrderIdIn(List<Long> orderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PaymentAllocation> findWithLockAllByOrderIdAndOriginalPaymentTransactionId(
        Long orderId,
        Long originalPaymentTransactionId
    );
}
