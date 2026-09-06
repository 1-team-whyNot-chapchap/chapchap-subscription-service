package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.response.SubscriptionCancellationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SubscriptionCancellationResponseService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPeriodRepository periods;
    private final RefundRepository refunds;

    public SubscriptionCancellationResponseService(SubscriptionRepository subscriptions,
        SubscriptionPeriodRepository periods, RefundRepository refunds) {
        this.subscriptions = subscriptions;
        this.periods = periods;
        this.refunds = refunds;
    }

    @Transactional(readOnly = true)
    public SubscriptionCancellationResponse create(Long subscriptionId, Long periodId,
        SubscriptionCancellationType type, LocalDateTime requestedAt, Long refundId) {
        var subscription = subscriptions.findById(subscriptionId).orElseThrow();
        var period = periods.findById(periodId).orElseThrow();
        SubscriptionCancellationResponse.RefundSummary refundSummary = refundId == null ? null
            : refunds.findById(refundId).map(refund -> new SubscriptionCancellationResponse.RefundSummary(
                refund.getPublicId(), refund.getStatus(), refund.getRefundAmount(),
                refund.getSuccessfulRefundAmount(),
                refund.getRefundAmount() - refund.getSuccessfulRefundAmount()
            )).orElseThrow();
        return new SubscriptionCancellationResponse(
            type, subscription.getStatus(), period.getStatus(), requestedAt, refundSummary
        );
    }
}
