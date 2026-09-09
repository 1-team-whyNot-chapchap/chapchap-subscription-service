package com.chapchap.subscription.domain.payment.repository;

import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import com.chapchap.subscription.domain.payment.entity.PaymentAttemptResult;

/** 외부 결제 응답으로 생성된 결제 처리 시도 이력을 저장하고 조회한다. */
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    /** 한 결제 거래의 시도 이력을 최초 시도부터 순서대로 조회한다. */
    List<PaymentAttempt> findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(Long paymentTransactionId);

    /** 동일한 외부 요청 응답이 중복 기록됐는지 멱등성 키로 확인한다. */
    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<PaymentAttempt> findTopByPaymentTransactionIdAndResultOrderByAttemptSequenceDesc(
        Long paymentTransactionId,
        PaymentAttemptResult result
    );
}
