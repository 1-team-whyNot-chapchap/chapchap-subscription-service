package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.repository.PaymentMethodRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.payment.service.FirstPaymentExecutionService;
import com.chapchap.subscription.domain.payment.service.PaymentCancellationExecutionService;
import com.chapchap.subscription.domain.payment.service.SettingChangePaymentPreparationService;
import com.chapchap.subscription.domain.payment.service.SettingChangeRefundPreparationService;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.subscription.request.SettingChangeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingChangeServiceTest {
    SettingChangePreparationFlowService preparation = mock(SettingChangePreparationFlowService.class);
    SettingChangeAmountService amounts = mock(SettingChangeAmountService.class);
    SettingChangeFinalizationService finalization = mock(SettingChangeFinalizationService.class);
    SettingChangeCompletionService completion = mock(SettingChangeCompletionService.class);
    PaymentMethodRepository paymentMethods = mock(PaymentMethodRepository.class);
    FirstPaymentExecutionService paymentExecution = mock(FirstPaymentExecutionService.class);
    KstReferenceTimeProvider time = mock(KstReferenceTimeProvider.class);
    SettingChangeService service;

    @BeforeEach
    void setUp() {
        service = new SettingChangeService(preparation, amounts, finalization,
            completion, paymentMethods,
            mock(SettingChangePaymentPreparationService.class), paymentExecution,
            mock(SettingChangePaymentCompletionService.class), mock(SettingChangeRefundPreparationService.class),
            mock(PaymentCancellationExecutionService.class), mock(SettingChangeRefundCompletionService.class),
            mock(RefundRepository.class), mock(SubscriptionRepository.class),
            mock(SubscriptionSettingRepository.class), time);
    }

    @Test
    void 차액이_없으면_외부결제없이_배분과_설정변경을_확정한다() {
        var setting = pendingSetting();
        var prepared = new PreparedSettingChange(1L, 2L, 2, now(), LocalDate.of(2026, 9, 8), 1);
        var snapshot = new SettingChangeAmountSnapshot(setting, List.of(), List.of(), List.of(), 10_000L, 10_000L);
        when(preparation.prepare(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(prepared);
        when(amounts.analyze(2L)).thenReturn(snapshot);
        when(time.now()).thenReturn(now());

        var response = service.change(10L, new SettingChangeRequest("PLN", List.of()));

        assertThat(response.settingStatus()).isEqualTo(SubscriptionSettingStatus.ACTIVE);
        assertThat(response.differenceType()).isEqualTo(com.chapchap.subscription.domain.subscription.response.SettingChangeResponse.DifferenceType.NO_PRICE_CHANGE);
        verify(finalization).approve(2L, now());
        verify(paymentExecution, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 증액이면_결제하지_않고_현재결제수단과_확인필요를_반환한다() {
        var setting = pendingSetting();
        var prepared = new PreparedSettingChange(1L, 2L, 2, now(), LocalDate.of(2026, 9, 8), 1);
        var snapshot = new SettingChangeAmountSnapshot(setting, List.of(), List.of(), List.of(), 10_000L, 13_000L);
        PaymentMethod method = PaymentMethod.createAsCurrent(10L, PaymentProviderCode.PORTONE,
            "protected", "카드사", "1234-****", now());
        when(preparation.prepare(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(prepared);
        when(amounts.analyze(2L)).thenReturn(snapshot);
        when(paymentMethods.findByUserIdAndStatusAndIsCurrentTrueAndDeletedAtIsNull(
            org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(method));

        var response = service.change(10L, new SettingChangeRequest("PLN", List.of()));

        assertThat(response.settingStatus()).isEqualTo(SubscriptionSettingStatus.CHANGE_PENDING);
        assertThat(response.paymentConfirmationRequired()).isTrue();
        assertThat(response.differenceAmount()).isEqualTo(3_000L);
        assertThat(response.currentPaymentMethod().cardCompany()).isEqualTo("카드사");
        verify(paymentExecution, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 증액인데_현재결제수단이_없으면_숨은_변경대기를_남기지_않는다() {
        var setting = pendingSetting();
        var prepared = new PreparedSettingChange(1L, 2L, 2, now(), LocalDate.of(2026, 9, 8), 1);
        when(preparation.prepare(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(prepared);
        when(amounts.analyze(2L)).thenReturn(new SettingChangeAmountSnapshot(setting,
            List.of(), List.of(), List.of(), 10_000L, 13_000L));
        when(paymentMethods.findByUserIdAndStatusAndIsCurrentTrueAndDeletedAtIsNull(
            org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(time.now()).thenReturn(now());

        assertThatThrownBy(() -> service.change(10L, new SettingChangeRequest("PLN", List.of())))
            .isInstanceOf(com.chapchap.subscription.domain.payment.service.exception.CurrentPaymentMethodUnavailableException.class);
        verify(completion).complete(2L, SettingChangeCompletionStatus.NOT_APPLIED, now());
    }

    private SubscriptionSetting pendingSetting() {
        return SubscriptionSetting.createChangePending(1L, 3L, 2, now(), LocalDate.of(2026, 9, 8));
    }

    private LocalDateTime now() { return LocalDateTime.of(2026, 9, 6, 12, 0); }
}
