package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.global.exception.subscription.SubscriptionNotFoundException;
import com.chapchap.subscription.global.exception.payment.PaymentTransactionProcessingException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionCancellationNotAllowedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionKafkaDeliveryCompletedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionPreStartCancellationDeadlinePassedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 첫 시작 취소와 결제된 다음 기간 전액 취소의 비결제 사전 조건을 판별한다. */
@Service
public class SubscriptionPreStartCancellationPreparationService {
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPeriodRepository periodRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final KstReferenceTimeProvider timeProvider;

    public SubscriptionPreStartCancellationPreparationService(SubscriptionRepository subscriptionRepository, SubscriptionPeriodRepository periodRepository, PaymentTransactionRepository paymentTransactionRepository, OrderRepository orderRepository, KstReferenceTimeProvider timeProvider) {
        this.subscriptionRepository = subscriptionRepository;
        this.periodRepository = periodRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.orderRepository = orderRepository;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public SubscriptionCancellationPreparation prepare(Long userId) {
        Subscription subscription = subscriptionRepository.findWithLockByUserId(userId).orElseThrow(SubscriptionNotFoundException::new);
        if (paymentTransactionRepository.existsBySubscriptionIdAndStatus(subscription.getId(), PaymentTransactionStatus.PROCESSING)) {
            throw new PaymentTransactionProcessingException();
        }
        SubscriptionPeriod target = periodRepository.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(subscription.getId(), SubscriptionPeriodStatus.SCHEDULED)
            .orElseThrow(SubscriptionCancellationNotAllowedException::new);
        SubscriptionCancellationType type = cancellationType(subscription, target);
        LocalDateTime referenceAt = timeProvider.now();
        if (!referenceAt.isBefore(target.getPeriodStartDate().minusDays(1).atTime(14, 0))) {
            throw new SubscriptionPreStartCancellationDeadlinePassedException();
        }
        if (orderRepository.existsBySubscriptionPeriodIdAndKafkaDeliveryStatus(target.getId(), OrderKafkaDeliveryStatus.COMPLETED)) {
            throw new SubscriptionKafkaDeliveryCompletedException();
        }
        return new SubscriptionCancellationPreparation(type, subscription.getId(), target.getId(), orderRepository.findAllBySubscriptionPeriodId(target.getId()).stream().map(order -> order.getId()).toList(), referenceAt);
    }

    private SubscriptionCancellationType cancellationType(Subscription subscription, SubscriptionPeriod target) {
        if (subscription.getStatus() == SubscriptionStatus.SCHEDULED) return SubscriptionCancellationType.CANCELLATION_BEFORE_START;
        if (subscription.getStatus() == SubscriptionStatus.IN_PROGRESS) return SubscriptionCancellationType.NEXT_PERIOD_FULL_CANCELLATION;
        throw new SubscriptionCancellationNotAllowedException();
    }
}
