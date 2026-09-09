package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.client.PaymentCancellationStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.PaymentCancellationCompletionService;
import com.chapchap.subscription.domain.payment.service.PaymentCancellationExecutionService;
import com.chapchap.subscription.domain.payment.service.PeriodRefundPreparationService;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.response.SubscriptionCancellationResponse;
import com.chapchap.subscription.global.exception.payment.PaymentCancellationFailedException;
import com.chapchap.subscription.global.exception.payment.PaymentProviderAuthenticationFailedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionCancellationNotAllowedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

/** 고객 요청 하나를 네 가지 해지·시작 취소 흐름 중 하나로 연결한다. */
@Service
public class SubscriptionCancellationService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPeriodRepository periods;
    private final PaymentTransactionRepository payments;
    private final SubscriptionCancellationPreparationService regularCancellation;
    private final SubscriptionRetryStopCancellationService retryStopCancellation;
    private final SubscriptionPreStartCancellationPreparationService preStartPreparation;
    private final PeriodRefundPreparationService refundPreparation;
    private final PaymentCancellationExecutionService cancellationExecution;
    private final PaymentCancellationCompletionService cancellationCompletion;
    private final SubscriptionPreStartCancellationCompletionService preStartCompletion;
    private final SubscriptionCancellationResponseService responses;
    private final KstReferenceTimeProvider time;

    public SubscriptionCancellationService(SubscriptionRepository subscriptions,
        SubscriptionPeriodRepository periods, PaymentTransactionRepository payments,
        SubscriptionCancellationPreparationService regularCancellation,
        SubscriptionRetryStopCancellationService retryStopCancellation,
        SubscriptionPreStartCancellationPreparationService preStartPreparation,
        PeriodRefundPreparationService refundPreparation,
        PaymentCancellationExecutionService cancellationExecution,
        PaymentCancellationCompletionService cancellationCompletion,
        SubscriptionPreStartCancellationCompletionService preStartCompletion,
        SubscriptionCancellationResponseService responses, KstReferenceTimeProvider time) {
        this.subscriptions = subscriptions;
        this.periods = periods;
        this.payments = payments;
        this.regularCancellation = regularCancellation;
        this.retryStopCancellation = retryStopCancellation;
        this.preStartPreparation = preStartPreparation;
        this.refundPreparation = refundPreparation;
        this.cancellationExecution = cancellationExecution;
        this.cancellationCompletion = cancellationCompletion;
        this.preStartCompletion = preStartCompletion;
        this.responses = responses;
        this.time = time;
    }

    public SubscriptionCancellationResponse cancel(Long userId) {
        var subscription = subscriptions.findByUserId(userId).orElseThrow(SubscriptionNotFoundException::new);
        if (subscription.getStatus() == SubscriptionStatus.SCHEDULED) {
            return cancelPaidPeriod(userId);
        }
        if (subscription.getStatus() != SubscriptionStatus.IN_PROGRESS) {
            throw new SubscriptionCancellationNotAllowedException();
        }

        var retryWaitingPeriod = periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
            subscription.getId(), SubscriptionPeriodStatus.AWAITING_CONFIRMATION
        );
        var retryWaiting = payments.findTopBySubscriptionIdAndStatusOrderByOccurredAtDescIdDesc(
            subscription.getId(), PaymentTransactionStatus.RETRY_WAITING
        );
        if (retryWaitingPeriod.isPresent() && retryWaiting.isPresent()
            && retryWaiting.get().getSubscriptionPeriodId().equals(retryWaitingPeriod.get().getId())) {
            if (!time.now().toLocalTime().isBefore(LocalTime.of(13, 0))) {
                throw new SubscriptionCancellationNotAllowedException();
            }
            retryStopCancellation.cancel(userId);
            return responses.create(subscription.getId(), retryWaitingPeriod.get().getId(),
                SubscriptionCancellationType.REGULAR_PAYMENT_RETRY_STOPPED,
                subscriptions.findById(subscription.getId()).orElseThrow().getCancellationRequestedAt(), null);
        }

        var scheduled = periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
            subscription.getId(), SubscriptionPeriodStatus.SCHEDULED
        );
        if (scheduled.isPresent() && hasCancelableAllocation(scheduled.get())) {
            return cancelPaidPeriod(userId);
        }

        SubscriptionPeriod current = periods
            .findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(
                subscription.getId(), SubscriptionPeriodStatus.IN_PROGRESS
            ).orElseThrow(SubscriptionCancellationNotAllowedException::new);
        regularCancellation.cancelRegular(userId);
        return responses.create(subscription.getId(), current.getId(),
            SubscriptionCancellationType.REGULAR_CANCELLATION,
            subscriptions.findById(subscription.getId()).orElseThrow().getCancellationRequestedAt(), null);
    }

    private SubscriptionCancellationResponse cancelPaidPeriod(Long userId) {
        SubscriptionCancellationPreparation prepared = preStartPreparation.prepare(userId);
        var refund = refundPreparation.prepare(prepared);
        if (refund.status() == RefundStatus.REVIEW_REQUIRED) {
            return responses.create(prepared.subscriptionId(), prepared.targetPeriodId(),
                prepared.cancellationType(), prepared.referenceAt(), refund.refundId());
        }
        while (refund.status() == RefundStatus.PENDING) {
            if (refund.cancellationTransactionIds().isEmpty()) {
                throw new IllegalStateException("Pending refund has no executable cancellation");
            }
            Long transactionId = refund.cancellationTransactionIds().getFirst();
            var execution = cancellationExecution.execute(transactionId);
            RefundStatus status = cancellationCompletion.complete(execution);
            if (!execution.providerResult().isSucceeded()) {
                if (execution.providerResult().status() == PaymentCancellationStatus.PROVIDER_CONFIGURATION_FAILED) {
                    throw new PaymentProviderAuthenticationFailedException();
                }
                if (status == RefundStatus.FAILED) throw new PaymentCancellationFailedException();
                return responses.create(prepared.subscriptionId(), prepared.targetPeriodId(),
                    prepared.cancellationType(), prepared.referenceAt(), refund.refundId());
            }
            refund = status == RefundStatus.PENDING
                ? refundPreparation.prepare(prepared)
                : new com.chapchap.subscription.domain.payment.service.PreparedPeriodRefund(
                    refund.refundId(), status, java.util.List.of()
                );
        }
        if (refund.status() != RefundStatus.COMPLETED) {
            throw new IllegalStateException("Refund did not reach a terminal completed state");
        }
        preStartCompletion.complete(prepared);
        return responses.create(prepared.subscriptionId(), prepared.targetPeriodId(),
            prepared.cancellationType(), prepared.referenceAt(), refund.refundId());
    }

    private boolean hasCancelableAllocation(SubscriptionPeriod period) {
        return payments.existsBySubscriptionIdAndSubscriptionPeriodIdAndStatus(
            period.getSubscriptionId(), period.getId(), PaymentTransactionStatus.SUCCESS
        );
    }
}
