package com.chapchap.subscription.domain.payment.repository;

import com.chapchap.subscription.domain.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 환불 업무 결과를 저장하고 대상 주문별로 조회한다. */
public interface RefundRepository extends JpaRepository<Refund, Long> {
    /** 인증 고객의 구독에 연결된 환불을 최신 요청 순서로 조회한다. */
    List<Refund> findAllBySubscriptionIdOrderByRequestedAtDescIdDesc(Long subscriptionId);

    /** 공개 식별자와 소유 구독으로 환불 상세 대상을 조회한다. */
    Optional<Refund> findByPublicIdAndSubscriptionId(String publicId, Long subscriptionId);

    Optional<Refund> findByOrderId(Long orderId);

    Optional<Refund> findBySubscriptionPeriodId(Long subscriptionPeriodId);

    Optional<Refund> findBySubscriptionSettingId(Long subscriptionSettingId);
}
