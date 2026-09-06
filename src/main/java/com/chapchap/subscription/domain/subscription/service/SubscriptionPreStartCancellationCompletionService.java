package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatusHistory;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
import com.chapchap.subscription.global.kafka.auth.AuthSubscriptionStatusPublisher;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.global.exception.subscription.SubscriptionKafkaDeliveryCompletedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전액 원 결제 취소 성공 후 시작 취소 또는 다음 기간 취소를 확정한다. */
@Service
public class SubscriptionPreStartCancellationCompletionService {
    private final SubscriptionRepository subscriptionRepository; private final SubscriptionPeriodRepository periodRepository; private final OrderRepository orderRepository; private final SubscriptionStatusHistoryRepository historyRepository; private final AuthSubscriptionStatusPublisher authPublisher; private final RefundRepository refundRepository;
    public SubscriptionPreStartCancellationCompletionService(SubscriptionRepository subscriptionRepository, SubscriptionPeriodRepository periodRepository, OrderRepository orderRepository, SubscriptionStatusHistoryRepository historyRepository, AuthSubscriptionStatusPublisher authPublisher, RefundRepository refundRepository) { this.subscriptionRepository = subscriptionRepository; this.periodRepository = periodRepository; this.orderRepository = orderRepository; this.historyRepository = historyRepository; this.authPublisher = authPublisher; this.refundRepository = refundRepository; }
    @Transactional
    public void complete(SubscriptionCancellationPreparation prepared) {
        var period = periodRepository.findWithLockById(prepared.targetPeriodId()).orElseThrow();
        Subscription subscription = subscriptionRepository.findWithLockById(prepared.subscriptionId()).orElseThrow();
        var refund = refundRepository.findBySubscriptionPeriodId(period.getId()).orElseThrow();
        if (refund.getStatus() != RefundStatus.COMPLETED) throw new IllegalStateException("Refund must be completed first");
        if (orderRepository.existsBySubscriptionPeriodIdAndKafkaDeliveryStatus(period.getId(), OrderKafkaDeliveryStatus.COMPLETED)) {
            throw new SubscriptionKafkaDeliveryCompletedException();
        }
        String cancellationReason = prepared.cancellationType() == SubscriptionCancellationType.CANCELLATION_BEFORE_START
            ? "FIRST_SUBSCRIPTION_CANCELLATION"
            : "NEXT_PERIOD_FULL_CANCELLATION";
        period.cancelBeforeStart(prepared.referenceAt(), cancellationReason);
        orderRepository.findAllBySubscriptionPeriodId(period.getId()).forEach(order -> order.cancelBeforeStart());
        SubscriptionStatus previous = subscription.getStatus();
        if (prepared.cancellationType() == SubscriptionCancellationType.CANCELLATION_BEFORE_START) subscription.cancelBeforeStart(prepared.referenceAt());
        else subscription.scheduleCancellation(prepared.referenceAt());
        historyRepository.save(SubscriptionStatusHistory.create(subscription.getId(), previous, subscription.getStatus(), "CUSTOMER", cancellationReason, prepared.referenceAt()));
        authPublisher.publishAfterCommit(subscription, previous, subscription.getStatus(), prepared.referenceAt());
    }
}
