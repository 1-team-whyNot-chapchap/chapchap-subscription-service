package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.client.PaymentCancellationResult;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.PaymentCancellationCompletionService;
import com.chapchap.subscription.domain.payment.service.PaymentCancellationExecutionResult;
import com.chapchap.subscription.domain.payment.service.PaymentCancellationExecutionService;
import com.chapchap.subscription.domain.payment.service.PeriodRefundPreparationService;
import com.chapchap.subscription.domain.payment.service.PreparedPeriodRefund;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.response.SubscriptionCancellationResponse;
import com.chapchap.subscription.global.exception.payment.PaymentCancellationFailedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionCancellationNotAllowedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionCancellationServiceTest {
    @Mock SubscriptionRepository subscriptions;
    @Mock SubscriptionPeriodRepository periods;
    @Mock PaymentTransactionRepository payments;
    @Mock SubscriptionCancellationPreparationService regular;
    @Mock SubscriptionRetryStopCancellationService retry;
    @Mock SubscriptionPreStartCancellationPreparationService preStart;
    @Mock PeriodRefundPreparationService refundPreparation;
    @Mock PaymentCancellationExecutionService execution;
    @Mock PaymentCancellationCompletionService completion;
    @Mock SubscriptionPreStartCancellationCompletionService preStartCompletion;
    @Mock SubscriptionCancellationResponseService responses;
    @Mock KstReferenceTimeProvider time;

    SubscriptionCancellationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionCancellationService(
            subscriptions, periods, payments, regular, retry, preStart, refundPreparation,
            execution, completion, preStartCompletion, responses, time
        );
    }

    @Test
    void 이용중이고_다음기간이_없으면_일반해지를_선택한다() {
        Subscription subscription = inProgressSubscription();
        SubscriptionPeriod current = period(1, SubscriptionPeriodStatus.IN_PROGRESS);
        SubscriptionCancellationResponse expected = org.mockito.Mockito.mock(SubscriptionCancellationResponse.class);
        when(subscriptions.findByUserId(10L)).thenReturn(Optional.of(subscription));
        when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(1L, SubscriptionPeriodStatus.SCHEDULED))
            .thenReturn(Optional.empty());
        when(payments.findTopBySubscriptionIdAndStatusOrderByOccurredAtDescIdDesc(1L, PaymentTransactionStatus.RETRY_WAITING))
            .thenReturn(Optional.empty());
        when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(1L, SubscriptionPeriodStatus.IN_PROGRESS))
            .thenReturn(Optional.of(current));
        when(subscriptions.findById(1L)).thenReturn(Optional.of(subscription));
        when(responses.create(1L, 11L, SubscriptionCancellationType.REGULAR_CANCELLATION, null, null))
            .thenReturn(expected);

        assertThat(service.cancel(10L)).isSameAs(expected);
        verify(regular).cancelRegular(10L);
    }

    @Test
    void 재시도대기_거래가_13시전이면_재시도중단을_우선한다() {
        Subscription subscription = inProgressSubscription();
        SubscriptionPeriod scheduled = period(2, SubscriptionPeriodStatus.SCHEDULED);
        PaymentTransaction transaction = org.mockito.Mockito.mock(PaymentTransaction.class);
        when(transaction.getSubscriptionPeriodId()).thenReturn(12L);
        when(subscriptions.findByUserId(10L)).thenReturn(Optional.of(subscription));
        when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(1L, SubscriptionPeriodStatus.SCHEDULED))
            .thenReturn(Optional.of(scheduled));
        when(payments.findTopBySubscriptionIdAndStatusOrderByOccurredAtDescIdDesc(1L, PaymentTransactionStatus.RETRY_WAITING))
            .thenReturn(Optional.of(transaction));
        when(time.now()).thenReturn(LocalDateTime.of(2026, 9, 6, 12, 59));
        when(subscriptions.findById(1L)).thenReturn(Optional.of(subscription));

        service.cancel(10L);

        verify(retry).cancel(10L);
        verify(regular, never()).cancelRegular(10L);
    }

    @Test
    void 재시도대기_거래는_13시부터_일반해지로_우회하지_않는다() {
        Subscription subscription = inProgressSubscription();
        SubscriptionPeriod scheduled = period(2, SubscriptionPeriodStatus.SCHEDULED);
        PaymentTransaction transaction = org.mockito.Mockito.mock(PaymentTransaction.class);
        when(transaction.getSubscriptionPeriodId()).thenReturn(12L);
        when(subscriptions.findByUserId(10L)).thenReturn(Optional.of(subscription));
        when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(1L, SubscriptionPeriodStatus.SCHEDULED))
            .thenReturn(Optional.of(scheduled));
        when(payments.findTopBySubscriptionIdAndStatusOrderByOccurredAtDescIdDesc(1L, PaymentTransactionStatus.RETRY_WAITING))
            .thenReturn(Optional.of(transaction));
        when(time.now()).thenReturn(LocalDateTime.of(2026, 9, 6, 13, 0));

        assertThatThrownBy(() -> service.cancel(10L))
            .isInstanceOf(SubscriptionCancellationNotAllowedException.class);
        verify(retry, never()).cancel(10L);
        verify(regular, never()).cancelRegular(10L);
    }

    @Test
    void 모든_원결제취소가_성공하면_시작취소를_확정한다() {
        Subscription subscription = scheduledSubscription();
        SubscriptionCancellationPreparation prepared = preparation();
        PaymentCancellationExecutionResult executed = executionResult(
            PaymentCancellationResult.succeeded("550e8400-e29b-41d4-a716-446655440000", "cancel-1")
        );
        SubscriptionCancellationResponse expected = org.mockito.Mockito.mock(SubscriptionCancellationResponse.class);
        when(subscriptions.findByUserId(10L)).thenReturn(Optional.of(subscription));
        when(preStart.prepare(10L)).thenReturn(prepared);
        when(refundPreparation.prepare(prepared)).thenReturn(
            new PreparedPeriodRefund(30L, RefundStatus.PENDING, List.of(40L))
        );
        when(execution.execute(40L)).thenReturn(executed);
        when(completion.complete(executed)).thenReturn(RefundStatus.COMPLETED);
        when(responses.create(1L, 12L, SubscriptionCancellationType.CANCELLATION_BEFORE_START,
            prepared.referenceAt(), 30L)).thenReturn(expected);

        assertThat(service.cancel(10L)).isSameAs(expected);
        verify(preStartCompletion).complete(prepared);
    }

    @Test
    void 첫_원결제취소가_명시실패하면_PAYMENT_013을_발생시킨다() {
        Subscription subscription = scheduledSubscription();
        SubscriptionCancellationPreparation prepared = preparation();
        PaymentCancellationExecutionResult executed = executionResult(
            PaymentCancellationResult.declined("550e8400-e29b-41d4-a716-446655440000", "DECLINED")
        );
        when(subscriptions.findByUserId(10L)).thenReturn(Optional.of(subscription));
        when(preStart.prepare(10L)).thenReturn(prepared);
        when(refundPreparation.prepare(prepared)).thenReturn(
            new PreparedPeriodRefund(30L, RefundStatus.PENDING, List.of(40L))
        );
        when(execution.execute(40L)).thenReturn(executed);
        when(completion.complete(executed)).thenReturn(RefundStatus.FAILED);

        assertThatThrownBy(() -> service.cancel(10L))
            .isInstanceOf(PaymentCancellationFailedException.class);
        verify(preStartCompletion, never()).complete(prepared);
    }

    private Subscription inProgressSubscription() {
        Subscription subscription = scheduledSubscription();
        subscription.startFirstPeriod();
        return subscription;
    }

    private Subscription scheduledSubscription() {
        Subscription subscription = Subscription.create(10L);
        ReflectionTestUtils.setField(subscription, "id", 1L);
        subscription.markScheduled();
        return subscription;
    }

    private SubscriptionPeriod period(int sequence, SubscriptionPeriodStatus status) {
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(
            1L, sequence, LocalDate.of(2026, 9, 8).plusDays(sequence), LocalDateTime.now()
        );
        ReflectionTestUtils.setField(period, "id", 10L + sequence);
        period.markScheduled();
        if (status == SubscriptionPeriodStatus.IN_PROGRESS) period.start();
        return period;
    }

    private SubscriptionCancellationPreparation preparation() {
        return new SubscriptionCancellationPreparation(
            SubscriptionCancellationType.CANCELLATION_BEFORE_START, 1L, 12L, List.of(20L),
            LocalDateTime.of(2026, 9, 6, 12, 0)
        );
    }

    private PaymentCancellationExecutionResult executionResult(PaymentCancellationResult providerResult) {
        return new PaymentCancellationExecutionResult(
            40L, PaymentProviderCode.PORTONE, "cancel-key-123456", 10_000L,
            LocalDateTime.of(2026, 9, 6, 12, 0), LocalDateTime.of(2026, 9, 6, 12, 0, 1),
            providerResult
        );
    }
}
