package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.service.command.FirstPaymentExecutionCommand;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import com.chapchap.subscription.domain.payment.service.result.PreparedRegularPayment;
import com.chapchap.subscription.domain.subscription.service.NextSubscriptionPeriodPreparationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 09시 정기결제와 13시 단 한 번의 재시도를 구독별로 격리해 실행한다. */
@Service
public class RegularPaymentService {
    private static final Logger log = LoggerFactory.getLogger(RegularPaymentService.class);
    private static final String ORDER_NAME = "챱챱 정기구독 결제";

    private final NextSubscriptionPeriodPreparationService nextPeriodPreparationService;
    private final RegularPaymentPreparationService preparationService;
    private final FirstPaymentExecutionService executionService;
    private final RegularPaymentCompletionService completionService;

    public RegularPaymentService(
        NextSubscriptionPeriodPreparationService nextPeriodPreparationService,
        RegularPaymentPreparationService preparationService,
        FirstPaymentExecutionService executionService,
        RegularPaymentCompletionService completionService
    ) {
        this.nextPeriodPreparationService = nextPeriodPreparationService;
        this.preparationService = preparationService;
        this.executionService = executionService;
        this.completionService = completionService;
    }

    /** 기준일에 종료되는 구독을 각각 독립적으로 오전 정기결제한다. */
    public void executeInitialPayments(LocalDateTime referenceAt) {
        List<Long> currentPeriodIds = nextPeriodPreparationService
            .findDueCurrentPeriodIds(referenceAt.toLocalDate());
        for (Long currentPeriodId : currentPeriodIds) {
            try {
                preparationService.prepareInitial(
                    currentPeriodId, referenceAt.toLocalDate(), referenceAt
                ).filter(PreparedRegularPayment::paymentRequired)
                    .ifPresent(prepared -> executeAndComplete(prepared, false));
            } catch (RuntimeException exception) {
                log.error("Regular payment initial attempt failed. currentPeriodId={}", currentPeriodId, exception);
            }
        }
    }

    /** 오전 실패 거래만 동일 거래로 오후에 한 번 재시도한다. */
    public void executeRetryPayments(LocalDateTime referenceAt) {
        for (Long transactionId : preparationService.findRetryWaitingTransactionIds(referenceAt.toLocalDate())) {
            try {
                executeAndComplete(preparationService.prepareRetry(transactionId), true);
            } catch (RuntimeException exception) {
                log.error("Regular payment retry failed. paymentTransactionId={}", transactionId, exception);
            }
        }
    }

    private void executeAndComplete(PreparedRegularPayment prepared, boolean finalAttempt) {
        FirstPaymentExecutionResult execution = executionService.execute(
            new FirstPaymentExecutionCommand(prepared.paymentTransactionId(), ORDER_NAME)
        );
        completionService.complete(
            execution,
            execution.providerResult().status() == AutomaticPaymentStatus.PAID
                ? prepared.allocations() : List.of(),
            finalAttempt
        );
    }
}
