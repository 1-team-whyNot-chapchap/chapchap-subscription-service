package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.service.FirstPaymentCompletionService;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/** 추가 결제 결과, 전체 배분 재구성, 주문 교체를 하나의 로컬 트랜잭션으로 확정한다. */
@Service
public class SettingChangePaymentCompletionService {
    private final FirstPaymentCompletionService payments;
    private final SettingChangeFinalizationService finalization;
    private final SettingChangeCompletionService completion;

    public SettingChangePaymentCompletionService(FirstPaymentCompletionService payments,
        SettingChangeFinalizationService finalization, SettingChangeCompletionService completion) {
        this.payments = payments;
        this.finalization = finalization;
        this.completion = completion;
    }

    @Transactional
    public AutomaticPaymentStatus complete(Long settingId, FirstPaymentExecutionResult execution,
        List<PaymentAllocationCommand> additionalAllocations, LocalDateTime completedAt) {
        AutomaticPaymentStatus status = execution.providerResult().status();
        payments.complete(execution, status == AutomaticPaymentStatus.PAID ? additionalAllocations : List.of());
        if (status == AutomaticPaymentStatus.PAID) {
            finalization.approve(settingId, completedAt);
        } else {
            completion.complete(settingId, SettingChangeCompletionStatus.NOT_APPLIED, completedAt);
        }
        return status;
    }
}
