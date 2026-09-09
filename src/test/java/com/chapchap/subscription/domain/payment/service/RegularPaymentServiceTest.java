package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.service.command.FirstPaymentExecutionCommand;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import com.chapchap.subscription.domain.payment.service.result.PreparedRegularPayment;
import com.chapchap.subscription.domain.subscription.service.NextSubscriptionPeriodPreparationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegularPaymentServiceTest {
    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 9, 30, 9, 0);
    @Mock private NextSubscriptionPeriodPreparationService nextPeriodPreparationService;
    @Mock private RegularPaymentPreparationService preparationService;
    @Mock private FirstPaymentExecutionService executionService;
    @Mock private RegularPaymentCompletionService completionService;
    private RegularPaymentService service;

    @BeforeEach
    void setUp() {
        service = new RegularPaymentService(
            nextPeriodPreparationService, preparationService, executionService, completionService
        );
    }

    @Test
    void 오전_배치는_한_구독이_실패해도_다음_구독을_계속_처리한다() {
        PreparedRegularPayment prepared = prepared(20L, true);
        FirstPaymentExecutionResult execution = success(20L);
        when(nextPeriodPreparationService.findDueCurrentPeriodIds(REFERENCE_AT.toLocalDate()))
            .thenReturn(List.of(1L, 2L));
        when(preparationService.prepareInitial(1L, REFERENCE_AT.toLocalDate(), REFERENCE_AT))
            .thenThrow(new IllegalStateException("broken subscription"));
        when(preparationService.prepareInitial(2L, REFERENCE_AT.toLocalDate(), REFERENCE_AT))
            .thenReturn(Optional.of(prepared));
        when(executionService.execute(any(FirstPaymentExecutionCommand.class))).thenReturn(execution);

        service.executeInitialPayments(REFERENCE_AT);

        verify(completionService).complete(execution, prepared.allocations(), false);
    }

    @Test
    void 기존_거래를_회수한_오전_배치는_외부결제를_중복호출하지_않는다() {
        when(nextPeriodPreparationService.findDueCurrentPeriodIds(REFERENCE_AT.toLocalDate()))
            .thenReturn(List.of(1L));
        when(preparationService.prepareInitial(1L, REFERENCE_AT.toLocalDate(), REFERENCE_AT))
            .thenReturn(Optional.of(prepared(10L, false)));

        service.executeInitialPayments(REFERENCE_AT);

        verify(executionService, never()).execute(any());
        verify(completionService, never()).complete(any(), any(), eq(false));
    }

    @Test
    void 오후_배치는_재시도대기_거래만_한번씩_실행한다() {
        PreparedRegularPayment prepared = prepared(10L, true);
        FirstPaymentExecutionResult execution = success(10L);
        when(preparationService.findRetryWaitingTransactionIds(REFERENCE_AT.toLocalDate())).thenReturn(List.of(10L));
        when(preparationService.prepareRetry(10L)).thenReturn(prepared);
        when(executionService.execute(any(FirstPaymentExecutionCommand.class))).thenReturn(execution);

        service.executeRetryPayments(REFERENCE_AT);

        verify(completionService).complete(execution, prepared.allocations(), true);
    }

    private PreparedRegularPayment prepared(Long transactionId, boolean required) {
        return new PreparedRegularPayment(
            transactionId, 2L, PaymentTransactionStatus.PROCESSING,
            List.of(new PaymentAllocationCommand(100L, 100_000L)), required
        );
    }

    private FirstPaymentExecutionResult success(Long transactionId) {
        return new FirstPaymentExecutionResult(
            transactionId, 30L, PaymentProviderCode.PORTONE, "request-key", 100_000L,
            REFERENCE_AT, REFERENCE_AT.plusSeconds(1),
            AutomaticPaymentResult.success("550e8400-e29b-41d4-a716-446655440000", "transaction-ref", "PAID")
        );
    }
}
