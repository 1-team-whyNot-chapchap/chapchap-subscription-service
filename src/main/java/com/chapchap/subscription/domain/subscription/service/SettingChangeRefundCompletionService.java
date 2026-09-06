package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.service.PaymentCancellationExecutionResult;
import com.chapchap.subscription.domain.payment.service.SettingChangeCancellationCompletionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** 감액 취소 결과와 최종 배분·주문 상태를 일관된 로컬 트랜잭션 경계로 확정한다. */
@Service
public class SettingChangeRefundCompletionService {
    private final SettingChangeCancellationCompletionService cancellations;
    private final SettingChangeFinalizationService finalization;
    private final SettingChangeCompletionService completion;

    public SettingChangeRefundCompletionService(SettingChangeCancellationCompletionService cancellations,
        SettingChangeFinalizationService finalization, SettingChangeCompletionService completion) {
        this.cancellations = cancellations;
        this.finalization = finalization;
        this.completion = completion;
    }

    @Transactional
    public RefundStatus complete(Long settingId, PaymentCancellationExecutionResult execution,
        LocalDateTime completedAt) {
        RefundStatus status = cancellations.complete(settingId, execution);
        if (status == RefundStatus.COMPLETED) {
            finalization.approve(settingId, completedAt);
        } else if (status == RefundStatus.FAILED) {
            completion.complete(settingId, SettingChangeCompletionStatus.NOT_APPLIED, completedAt);
        }
        return status;
    }
}
