package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatusHistory;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
import com.chapchap.subscription.global.exception.subscription.SubscriptionNotFoundException;
import com.chapchap.subscription.global.exception.payment.PaymentTransactionProcessingException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionCancellationNotAllowedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chapchap.subscription.global.kafka.customer.CustomerSubscriptionNotificationPublisher;

/** SUB-FN-007 일반 해지의 비결제 상태 전환을 처리한다. */
@Service
public class SubscriptionCancellationPreparationService {
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionStatusHistoryRepository historyRepository;
    private final KstReferenceTimeProvider timeProvider;
    private final CustomerSubscriptionNotificationPublisher customerNotificationPublisher;

    public SubscriptionCancellationPreparationService(SubscriptionRepository subscriptionRepository, PaymentTransactionRepository paymentTransactionRepository, SubscriptionStatusHistoryRepository historyRepository, KstReferenceTimeProvider timeProvider, CustomerSubscriptionNotificationPublisher customerNotificationPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.historyRepository = historyRepository;
        this.timeProvider = timeProvider;
        this.customerNotificationPublisher = customerNotificationPublisher;
    }

    @Transactional
    public void cancelRegular(Long userId) {
        Subscription subscription = subscriptionRepository.findWithLockByUserId(userId).orElseThrow(SubscriptionNotFoundException::new);
        if (paymentTransactionRepository.existsBySubscriptionIdAndStatus(subscription.getId(), PaymentTransactionStatus.PROCESSING)) {
            throw new PaymentTransactionProcessingException();
        }
        if (subscription.getStatus() != SubscriptionStatus.IN_PROGRESS) {
            throw new SubscriptionCancellationNotAllowedException();
        }
        var requestedAt = timeProvider.now();
        SubscriptionStatus previous = subscription.scheduleCancellation(requestedAt);
        historyRepository.save(SubscriptionStatusHistory.create(subscription.getId(), previous, SubscriptionStatus.CANCELLATION_SCHEDULED, "CUSTOMER", "REGULAR_CANCELLATION_REQUESTED", requestedAt));
        customerNotificationPublisher.publishNextPeriodCancellationAfterCommit(
            subscription, "NOT_REQUIRED", requestedAt
        );
    }
}
