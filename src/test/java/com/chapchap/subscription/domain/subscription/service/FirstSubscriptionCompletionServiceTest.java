package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.service.FirstOrderPreparationResult;
import com.chapchap.subscription.domain.order.service.FirstOrderService;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.service.FirstPaymentCompletionService;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatusHistory;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
import com.chapchap.subscription.global.kafka.auth.AuthSubscriptionStatusPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirstSubscriptionCompletionServiceTest {
    @Mock private FirstPaymentCompletionService paymentCompletionService;
    @Mock private FirstOrderService firstOrderService;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPeriodRepository periodRepository;
    @Mock private SubscriptionSettingRepository settingRepository;
    @Mock private SubscriptionStatusHistoryRepository historyRepository;
    @Mock private KstReferenceTimeProvider timeProvider;
    @Mock private AuthSubscriptionStatusPublisher authStatusPublisher;

    private FirstSubscriptionCompletionService service;
    private Subscription subscription;
    private SubscriptionPeriod period;
    private SubscriptionSetting setting;

    @BeforeEach
    void setUp() {
        service = new FirstSubscriptionCompletionService(
            paymentCompletionService,
            firstOrderService,
            subscriptionRepository,
            periodRepository,
            settingRepository,
            historyRepository,
            timeProvider, authStatusPublisher
        );
        subscription = Subscription.create(10L);
        ReflectionTestUtils.setField(subscription, "id", 1L);
        period = SubscriptionPeriod.createAwaitingConfirmation(
            1L, 1, LocalDate.of(2026, 9, 7), LocalDateTime.of(2026, 9, 4, 10, 0)
        );
        ReflectionTestUtils.setField(period, "id", 2L);
        setting = SubscriptionSetting.createFirstAwaitingConfirmation(
            1L, 20L, LocalDate.of(2026, 9, 7)
        );
        ReflectionTestUtils.setField(setting, "id", 3L);

        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(subscription));
        when(periodRepository.findById(2L)).thenReturn(Optional.of(period));
        when(settingRepository.findById(3L)).thenReturn(Optional.of(setting));
        when(timeProvider.now()).thenReturn(LocalDateTime.of(2026, 9, 4, 10, 1));
    }

    @Test
    void 결제_성공은_구독_기간_설정_주문과_할인_이력을_함께_확정한다() {
        PreparedFirstSubscription prepared = prepared(true);
        FirstPaymentExecutionResult execution = execution(AutomaticPaymentResult.success(
            "51111111-1111-4111-8111-111111111111", "TX-1", "PAID"
        ));

        AutomaticPaymentStatus result = service.complete(prepared, execution);

        assertThat(result).isEqualTo(AutomaticPaymentStatus.PAID);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.SCHEDULED);
        assertThat(subscription.isFirstSubscriptionDiscountUsed()).isTrue();
        assertThat(period.getStatus()).isEqualTo(SubscriptionPeriodStatus.SCHEDULED);
        assertThat(setting.getStatus()).isEqualTo(SubscriptionSettingStatus.ACTIVE);
        assertThat(setting.getConfirmedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 10, 1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PaymentAllocationCommand>> allocations = ArgumentCaptor.forClass(List.class);
        verify(paymentCompletionService).complete(eq(execution), allocations.capture());
        assertThat(allocations.getValue())
            .extracting(PaymentAllocationCommand::orderId, PaymentAllocationCommand::allocationAmount)
            .containsExactly(tuple(4L, 10_000L), tuple(5L, 20_000L));
        verify(firstOrderService).activateAfterPayment(2L, List.of(4L, 5L));
        verify(authStatusPublisher).publishAfterCommit(subscription, SubscriptionStatus.AWAITING_CONFIRMATION,
            SubscriptionStatus.SCHEDULED, LocalDateTime.of(2026, 9, 4, 10, 1));
        assertHistory(SubscriptionStatus.SCHEDULED, "FIRST_PAYMENT_SUCCEEDED");
    }

    @Test
    void 명시적_결제_거절은_모든_사전_데이터를_결제실패로_확정한다() {
        PreparedFirstSubscription prepared = prepared(true);
        FirstPaymentExecutionResult execution = execution(AutomaticPaymentResult.declined(
            "51111111-1111-4111-8111-111111111111", "DECLINED", "card declined"
        ));

        AutomaticPaymentStatus result = service.complete(prepared, execution);

        assertThat(result).isEqualTo(AutomaticPaymentStatus.DECLINED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
        assertThat(subscription.isFirstSubscriptionDiscountUsed()).isFalse();
        assertThat(period.getStatus()).isEqualTo(SubscriptionPeriodStatus.PAYMENT_FAILED);
        assertThat(setting.getStatus()).isEqualTo(SubscriptionSettingStatus.PAYMENT_FAILED);
        verify(paymentCompletionService).complete(execution, List.of());
        verify(firstOrderService).markPaymentFailed(2L, List.of(4L, 5L));
        assertHistory(SubscriptionStatus.PAYMENT_FAILED, "FIRST_PAYMENT_DECLINED");
    }

    private void assertHistory(SubscriptionStatus nextStatus, String reason) {
        ArgumentCaptor<SubscriptionStatusHistory> captor =
            ArgumentCaptor.forClass(SubscriptionStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousStatus())
            .isEqualTo(SubscriptionStatus.AWAITING_CONFIRMATION);
        assertThat(captor.getValue().getNextStatus()).isEqualTo(nextStatus);
        assertThat(captor.getValue().getChangeReason()).isEqualTo(reason);
        assertThat(captor.getValue().getChangeActor()).isEqualTo("SYSTEM");
    }

    private PreparedFirstSubscription prepared(boolean discountApplied) {
        return new PreparedFirstSubscription(
            1L,
            "11111111-1111-4111-8111-111111111111",
            2L,
            3L,
            SubscriptionStatus.AWAITING_CONFIRMATION,
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 10, 4),
            6L,
            new FirstOrderPreparationResult(
                List.of(
                    new FirstOrderPreparationResult.OrderAmount(4L, 10_000L),
                    new FirstOrderPreparationResult.OrderAmount(5L, 20_000L)
                ),
                30_000L,
                discountApplied
            ),
            true
        );
    }

    private FirstPaymentExecutionResult execution(AutomaticPaymentResult providerResult) {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 4, 10, 0);
        return new FirstPaymentExecutionResult(
            6L,
            7L,
            PaymentProviderCode.PORTONE,
            "idem-1",
            30_000L,
            requestedAt,
            requestedAt.plusSeconds(1),
            providerResult
        );
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
