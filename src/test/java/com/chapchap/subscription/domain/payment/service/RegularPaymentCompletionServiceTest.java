package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.global.kafka.customer.CustomerPaymentEventPublisher;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegularPaymentCompletionServiceTest {
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 9, 30, 9, 0);
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private PaymentAttemptRepository attemptRepository;
    @Mock private PaymentAllocationRepository allocationRepository;
    @Mock private SubscriptionPeriodRepository periodRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerPaymentEventPublisher customerPaymentPublisher;
    private RegularPaymentCompletionService service;

    @BeforeEach
    void setUp() {
        service = new RegularPaymentCompletionService(
            transactionRepository, attemptRepository, allocationRepository, periodRepository, orderRepository,
            customerPaymentPublisher
        );
    }

    @Test
    void 오전_성공은_시도와_정기결제_배분을_기록하고_기간과_주문을_활성화한다() {
        PaymentTransaction transaction = transaction("request-1");
        SubscriptionPeriod period = period();
        Order first = org.mockito.Mockito.mock(Order.class);
        Order second = org.mockito.Mockito.mock(Order.class);
        stubCommon(transaction, List.of());
        when(periodRepository.findWithLockById(2L)).thenReturn(Optional.of(period));
        when(orderRepository.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of(first, second));

        AutomaticPaymentStatus status = service.complete(
            success(transaction, "request-1"),
            List.of(new PaymentAllocationCommand(100L, 40_000L),
                new PaymentAllocationCommand(101L, 60_000L)),
            false
        );

        assertThat(status).isEqualTo(AutomaticPaymentStatus.PAID);
        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(period.getStatus()).isEqualTo(SubscriptionPeriodStatus.SCHEDULED);
        verify(first).activateAfterPayment();
        verify(second).activateAfterPayment();
        ArgumentCaptor<List<PaymentAllocation>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2)
            .allMatch(value -> value.getAllocationType()
                == com.chapchap.subscription.domain.payment.entity.PaymentAllocationType.REGULAR_PAYMENT);
        verify(customerPaymentPublisher).publishCompletedAfterCommit(
            org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        );
    }

    @Test
    void 오전_명시적_실패는_시도만_기록하고_재시도_대기로_남긴다() {
        PaymentTransaction transaction = transaction("request-1");
        stubCommon(transaction, List.of());

        service.complete(failure(transaction, "request-1"), List.of(), false);

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.RETRY_WAITING);
        verify(attemptRepository).save(any(PaymentAttempt.class));
        verify(periodRepository, never()).findWithLockById(any());
        verify(allocationRepository, never()).saveAll(any());
        verify(customerPaymentPublisher).publishRegularFailureAfterCommit(
            org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any(LocalDateTime.class),
            org.mockito.ArgumentMatchers.eq(false)
        );
    }

    @Test
    void 오후_재시도_실패는_두번째_시도를_기록하고_기간과_주문을_결제실패로_확정한다() {
        PaymentTransaction transaction = transaction("request-1");
        transaction.waitForRegularPaymentRetry();
        transaction.startRegularPaymentRetry("request-2");
        SubscriptionPeriod period = period();
        Order order = org.mockito.Mockito.mock(Order.class);
        stubCommon(transaction, List.of(org.mockito.Mockito.mock(PaymentAttempt.class)));
        when(periodRepository.findWithLockById(2L)).thenReturn(Optional.of(period));
        when(orderRepository.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of(order));

        service.complete(failure(transaction, "request-2"), List.of(), true);

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(period.getStatus()).isEqualTo(SubscriptionPeriodStatus.PAYMENT_FAILED);
        verify(order).markPaymentFailed();
        ArgumentCaptor<PaymentAttempt> captor = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(attemptRepository).save(captor.capture());
        assertThat(captor.getValue().getAttemptSequence()).isEqualTo(2);
        verify(customerPaymentPublisher).publishRegularFailureAfterCommit(
            org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any(LocalDateTime.class),
            org.mockito.ArgumentMatchers.eq(true)
        );
    }

    private void stubCommon(PaymentTransaction transaction, List<PaymentAttempt> attempts) {
        when(transactionRepository.findWithLockById(10L)).thenReturn(Optional.of(transaction));
        when(attemptRepository.findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(10L))
            .thenReturn(attempts);
        when(attemptRepository.existsByIdempotencyKey(any())).thenReturn(false);
    }

    private PaymentTransaction transaction(String requestKey) {
        PaymentTransaction transaction = PaymentTransaction.createRegularPayment(
            5L, 1L, 2L, 100_000L, REQUESTED_AT, LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 28), requestKey, REQUESTED_AT
        );
        ReflectionTestUtils.setField(transaction, "id", 10L);
        return transaction;
    }

    private SubscriptionPeriod period() {
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(
            1L, 2, LocalDate.of(2026, 10, 1), REQUESTED_AT
        );
        ReflectionTestUtils.setField(period, "id", 2L);
        return period;
    }

    private FirstPaymentExecutionResult success(PaymentTransaction transaction, String key) {
        return execution(transaction, key, AutomaticPaymentResult.success(
            transaction.getPublicId(), "transaction-ref", "PAID"
        ));
    }

    private FirstPaymentExecutionResult failure(PaymentTransaction transaction, String key) {
        return execution(transaction, key, AutomaticPaymentResult.declined(
            transaction.getPublicId(), "DECLINED", "결제가 거절되었습니다."
        ));
    }

    private FirstPaymentExecutionResult execution(
        PaymentTransaction transaction,
        String key,
        AutomaticPaymentResult result
    ) {
        return new FirstPaymentExecutionResult(
            10L, 20L, PaymentProviderCode.PORTONE, key, 100_000L,
            REQUESTED_AT, REQUESTED_AT.plusSeconds(1), result
        );
    }
}
