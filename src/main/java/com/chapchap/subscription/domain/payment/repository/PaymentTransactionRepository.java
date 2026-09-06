package com.chapchap.subscription.domain.payment.repository;

import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 결제 거래의 저장과 업무 중복·처리 상태 조회를 담당한다. */
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    /** 인증 고객의 결제·원 결제 취소 거래를 최신 발생 순서로 조회한다. */
    List<PaymentTransaction> findAllByUserIdOrderByOccurredAtDescIdDesc(Long userId);

    /** 내부 업무 키로 이미 생성된 동일 업무의 결제 거래를 조회한다. */
    Optional<PaymentTransaction> findByBusinessDeduplicationKey(String businessDeduplicationKey);

    /** 인증 고객의 공개 결제 식별자로 본인 소유 결제 거래를 조회한다. */
    Optional<PaymentTransaction> findByPublicIdAndUserId(String publicId, Long userId);

    /** 환불에 연결된 원 결제 취소 거래를 실제 발생 순서대로 조회한다. */
    List<PaymentTransaction> findAllByRefundIdOrderByOccurredAtAscIdAsc(Long refundId);

    /** 특정 구독에 지정한 상태의 결제 거래가 존재하는지 확인한다. */
    boolean existsBySubscriptionIdAndStatus(Long subscriptionId, PaymentTransactionStatus status);

    Optional<PaymentTransaction> findTopBySubscriptionIdAndStatusOrderByOccurredAtDescIdDesc(
        Long subscriptionId,
        PaymentTransactionStatus status
    );

    boolean existsBySubscriptionIdAndSubscriptionPeriodIdAndStatus(
        Long subscriptionId,
        Long subscriptionPeriodId,
        PaymentTransactionStatus status
    );
}
