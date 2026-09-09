package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.service.FirstOrderPreparationResult;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.service.FirstPaymentExecutionService;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.request.FirstSubscriptionRequest;
import com.chapchap.subscription.domain.subscription.response.FirstSubscriptionResponse;
import com.chapchap.subscription.global.exception.payment.PaymentDeclinedException;
import com.chapchap.subscription.global.exception.payment.PaymentProviderAuthenticationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirstSubscriptionServiceTest {
    @Mock private FirstSubscriptionPreparationService preparationService;
    @Mock private FirstPaymentExecutionService paymentExecutionService;
    @Mock private FirstSubscriptionCompletionService completionService;

    private FirstSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new FirstSubscriptionService(preparationService, paymentExecutionService, completionService);
    }

    @Test
    void 처리중_재요청은_PG를_다시_호출하지_않고_기존_응답을_반환한다() {
        PreparedFirstSubscription prepared = PreparedFirstSubscription.processing(
            1L, "11111111-1111-4111-8111-111111111111", 2L,
            LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 4), 3L
        );
        when(preparationService.prepare(10L, request())).thenReturn(prepared);

        FirstSubscriptionResponse response = service.subscribe(10L, request());

        assertThat(response.subscriptionStatus()).isEqualTo(SubscriptionStatus.AWAITING_CONFIRMATION);
        verify(paymentExecutionService, never()).execute(any());
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    void 결제_성공은_로컬_확정_뒤_SCHEDULED를_반환한다() {
        PreparedFirstSubscription prepared = preparedForPayment();
        FirstPaymentExecutionResult execution = execution(AutomaticPaymentResult.success(
            "51111111-1111-4111-8111-111111111111", "TX-1", "PAID"
        ));
        when(preparationService.prepare(10L, request())).thenReturn(prepared);
        when(paymentExecutionService.execute(any())).thenReturn(execution);
        when(completionService.complete(prepared, execution)).thenReturn(AutomaticPaymentStatus.PAID);

        FirstSubscriptionResponse response = service.subscribe(10L, request());

        assertThat(response.subscriptionStatus()).isEqualTo(SubscriptionStatus.SCHEDULED);
        verify(completionService).complete(prepared, execution);
    }

    @Test
    void 명시적_결제_거절은_실패_확정_뒤_PAYMENT_008을_발생시킨다() {
        PreparedFirstSubscription prepared = preparedForPayment();
        FirstPaymentExecutionResult execution = execution(AutomaticPaymentResult.declined(
            "51111111-1111-4111-8111-111111111111", "DECLINED", "card declined"
        ));
        when(preparationService.prepare(10L, request())).thenReturn(prepared);
        when(paymentExecutionService.execute(any())).thenReturn(execution);
        when(completionService.complete(prepared, execution)).thenReturn(AutomaticPaymentStatus.DECLINED);

        assertThatThrownBy(() -> service.subscribe(10L, request()))
            .isInstanceOf(PaymentDeclinedException.class);
        verify(completionService).complete(prepared, execution);
    }

    @Test
    void Provider_설정_오류는_실패_확정_뒤_PAYMENT_002를_발생시킨다() {
        PreparedFirstSubscription prepared = preparedForPayment();
        FirstPaymentExecutionResult execution = execution(AutomaticPaymentResult.providerConfigurationFailed(
            "51111111-1111-4111-8111-111111111111", "AUTH_FAILED", "provider configuration"
        ));
        when(preparationService.prepare(10L, request())).thenReturn(prepared);
        when(paymentExecutionService.execute(any())).thenReturn(execution);
        when(completionService.complete(prepared, execution))
            .thenReturn(AutomaticPaymentStatus.PROVIDER_CONFIGURATION_FAILED);

        assertThatThrownBy(() -> service.subscribe(10L, request()))
            .isInstanceOf(PaymentProviderAuthenticationFailedException.class);
        verify(completionService).complete(prepared, execution);
    }

    @Test
    void 동시_prepare_UNIQUE_충돌은_기존_PROCESSING을_반환하고_PG를_호출하지_않는다() {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("concurrent prepare");
        PreparedFirstSubscription processing = processing();
        when(preparationService.prepare(10L, request())).thenThrow(conflict);
        when(preparationService.recoverConcurrentProcessing(10L)).thenReturn(Optional.of(processing));

        FirstSubscriptionResponse response = service.subscribe(10L, request());

        assertThat(response.subscriptionStatus()).isEqualTo(SubscriptionStatus.AWAITING_CONFIRMATION);
        verify(paymentExecutionService, never()).execute(any());
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    void 동시_prepare_복구_조건이_맞지_않으면_원래_DB_오류를_숨기지_않는다() {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("unrelated constraint");
        when(preparationService.prepare(10L, request())).thenThrow(conflict);
        when(preparationService.recoverConcurrentProcessing(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subscribe(10L, request())).isSameAs(conflict);
        verify(paymentExecutionService, never()).execute(any());
        verify(completionService, never()).complete(any(), any());
    }

    private FirstSubscriptionRequest request() {
        return new FirstSubscriptionRequest(
            "11111111-1111-4111-8111-111111111111",
            List.of(new FirstSubscriptionRequest.DeliveryCondition(
                DeliveryWeekday.MONDAY,
                2,
                "21111111-1111-4111-8111-111111111111",
                DeliveryTimeSlot.TIME_1100_1300
            ))
        );
    }

    private PreparedFirstSubscription preparedForPayment() {
        return new PreparedFirstSubscription(
            1L,
            "11111111-1111-4111-8111-111111111111",
            2L,
            3L,
            SubscriptionStatus.AWAITING_CONFIRMATION,
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 10, 4),
            4L,
            new FirstOrderPreparationResult(
                List.of(new FirstOrderPreparationResult.OrderAmount(5L, 10_000L)),
                10_000L,
                true
            ),
            true
        );
    }

    private PreparedFirstSubscription processing() {
        return PreparedFirstSubscription.processing(
            1L,
            "11111111-1111-4111-8111-111111111111",
            2L,
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 10, 4),
            4L
        );
    }

    private FirstPaymentExecutionResult execution(AutomaticPaymentResult providerResult) {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 4, 10, 0);
        return new FirstPaymentExecutionResult(
            4L,
            6L,
            PaymentProviderCode.PORTONE,
            "idem-1",
            10_000L,
            requestedAt,
            requestedAt.plusSeconds(1),
            providerResult
        );
    }
}
