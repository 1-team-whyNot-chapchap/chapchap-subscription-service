package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.service.FirstPaymentCompletionService;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import com.chapchap.subscription.global.kafka.customer.CustomerPaymentEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SettingChangePaymentCompletionServiceTest {
    @Test
    void 추가결제_성공은_설정확정_뒤_결제완료_Event를_연결한다() {
        FirstPaymentCompletionService payments = mock(FirstPaymentCompletionService.class);
        SettingChangeFinalizationService finalization = mock(SettingChangeFinalizationService.class);
        SettingChangeCompletionService completion = mock(SettingChangeCompletionService.class);
        CustomerPaymentEventPublisher publisher = mock(CustomerPaymentEventPublisher.class);
        SettingChangePaymentCompletionService service = new SettingChangePaymentCompletionService(
            payments, finalization, completion, publisher
        );
        FirstPaymentExecutionResult execution = execution(AutomaticPaymentResult.success(
            "11111111-1111-4111-8111-111111111111", "transaction", "PAID"
        ));

        AutomaticPaymentStatus status = service.complete(
            2L, execution, List.of(), LocalDateTime.of(2026, 9, 6, 14, 1)
        );

        assertThat(status).isEqualTo(AutomaticPaymentStatus.PAID);
        verify(finalization).approve(2L, LocalDateTime.of(2026, 9, 6, 14, 1));
        verify(publisher).publishCompletedAfterCommit(10L, execution.respondedAt());
        verify(completion, never()).complete(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void 추가결제_실패는_미적용_확정_뒤_결제실패_Event를_연결한다() {
        FirstPaymentCompletionService payments = mock(FirstPaymentCompletionService.class);
        SettingChangeFinalizationService finalization = mock(SettingChangeFinalizationService.class);
        SettingChangeCompletionService completion = mock(SettingChangeCompletionService.class);
        CustomerPaymentEventPublisher publisher = mock(CustomerPaymentEventPublisher.class);
        SettingChangePaymentCompletionService service = new SettingChangePaymentCompletionService(
            payments, finalization, completion, publisher
        );
        FirstPaymentExecutionResult execution = execution(AutomaticPaymentResult.declined(
            "11111111-1111-4111-8111-111111111111", "DECLINED", "declined"
        ));

        AutomaticPaymentStatus status = service.complete(
            2L, execution, List.of(), LocalDateTime.of(2026, 9, 6, 14, 1)
        );

        assertThat(status).isEqualTo(AutomaticPaymentStatus.DECLINED);
        verify(completion).complete(
            2L, SettingChangeCompletionStatus.NOT_APPLIED, LocalDateTime.of(2026, 9, 6, 14, 1)
        );
        verify(publisher).publishSettingChangeFailureAfterCommit(10L, execution.respondedAt());
        verify(finalization, never()).approve(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()
        );
    }

    private FirstPaymentExecutionResult execution(AutomaticPaymentResult result) {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 6, 14, 0);
        return new FirstPaymentExecutionResult(
            10L, 20L, PaymentProviderCode.PORTONE, "setting-payment-key", 5_000L,
            requestedAt, requestedAt.plusSeconds(1), result
        );
    }
}
