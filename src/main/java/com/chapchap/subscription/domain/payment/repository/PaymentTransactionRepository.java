package com.chapchap.subscription.domain.payment.repository;

import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
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

    /** 해지와 13시 재시도의 상태 경합을 막도록 최신 재시도 대기 거래를 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findTopWithLockBySubscriptionIdAndStatusOrderByOccurredAtDescIdDesc(
        Long subscriptionId,
        PaymentTransactionStatus status
    );

    /** 정기결제 배치가 당일 09시에 시작한 재시도 대기 거래를 생성 순서대로 조회한다. */
    List<PaymentTransaction> findAllByStatusAndProcessingReferenceAtGreaterThanEqualAndProcessingReferenceAtLessThanOrderByIdAsc(
        PaymentTransactionStatus status,
        LocalDateTime referenceStart,
        LocalDateTime referenceEndExclusive
    );

    /** 결제 상태 전이를 직렬화하기 위해 거래를 쓰기 잠금으로 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findWithLockById(Long id);

    List<PaymentTransaction> findAllByOriginalPaymentTransactionIdAndStatus(
        Long originalPaymentTransactionId,
        PaymentTransactionStatus status
    );
}
