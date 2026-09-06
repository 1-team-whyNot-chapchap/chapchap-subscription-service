package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import org.springframework.stereotype.Service;

/** 검증된 배송 환불 사실 하나를 중복 없이 순차 처리한다. */
@Service
public class DeliveryRefundService {
    private final DeliveryRefundPreparationService preparation;
    private final PaymentCancellationExecutionService execution;
    private final DeliveryRefundCancellationCompletionService completion;

    public DeliveryRefundService(
        DeliveryRefundPreparationService preparation,
        PaymentCancellationExecutionService execution,
        DeliveryRefundCancellationCompletionService completion
    ) {
        this.preparation = preparation;
        this.execution = execution;
        this.completion = completion;
    }

    public RefundStatus process(DeliveryRefundCommand command) {
        PreparedDeliveryRefund prepared = preparation.start(command);
        if (prepared.duplicate() || prepared.cancellationTransactionId() == null) {
            return prepared.status();
        }

        while (prepared.status() == RefundStatus.PENDING) {
            PaymentCancellationExecutionResult result = execution.execute(prepared.cancellationTransactionId());
            RefundStatus status = completion.complete(result);
            if (status != RefundStatus.PENDING) return status;
            prepared = preparation.prepareNext(prepared.refundId());
            if (prepared.cancellationTransactionId() == null) return prepared.status();
        }
        return prepared.status();
    }
}
