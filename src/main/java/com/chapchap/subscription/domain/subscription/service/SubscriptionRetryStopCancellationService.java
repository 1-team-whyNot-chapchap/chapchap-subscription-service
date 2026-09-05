package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatusHistory;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 09시 정기결제 실패 뒤 13시 전 고객 해지로 재시도를 중단한다. */
@Service
public class SubscriptionRetryStopCancellationService {
    private final SubscriptionRepository subscriptions; private final SubscriptionPeriodRepository periods; private final PaymentTransactionRepository payments; private final OrderRepository orders; private final SubscriptionStatusHistoryRepository histories; private final KstReferenceTimeProvider time;
    public SubscriptionRetryStopCancellationService(SubscriptionRepository subscriptions, SubscriptionPeriodRepository periods, PaymentTransactionRepository payments, OrderRepository orders, SubscriptionStatusHistoryRepository histories, KstReferenceTimeProvider time) { this.subscriptions=subscriptions; this.periods=periods; this.payments=payments; this.orders=orders; this.histories=histories; this.time=time; }
    @Transactional
    public void cancel(Long userId) {
        var subscription = subscriptions.findWithLockByUserId(userId).orElseThrow();
        var now = time.now();
        if (now.toLocalTime().compareTo(java.time.LocalTime.of(13, 0)) >= 0 || subscription.getStatus() != SubscriptionStatus.IN_PROGRESS) throw new IllegalStateException("재시도 중단 해지 조건이 아닙니다.");
        var transaction = payments.findAllByUserIdOrderByOccurredAtDescIdDesc(userId).stream().filter(value -> value.getSubscriptionId().equals(subscription.getId()) && value.getStatus() == PaymentTransactionStatus.RETRY_WAITING).findFirst().orElseThrow();
        var period = periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(subscription.getId(), SubscriptionPeriodStatus.SCHEDULED).orElseThrow();
        transaction.stopRetry(); period.cancelBeforeStart(now, "REGULAR_PAYMENT_RETRY_STOPPED"); orders.findAllBySubscriptionPeriodId(period.getId()).forEach(order -> order.cancelBeforeStart());
        SubscriptionStatus previous = subscription.scheduleCancellation(now);
        histories.save(SubscriptionStatusHistory.create(subscription.getId(), previous, SubscriptionStatus.CANCELLATION_SCHEDULED, "CUSTOMER", "REGULAR_PAYMENT_RETRY_STOPPED", now));
    }
}
