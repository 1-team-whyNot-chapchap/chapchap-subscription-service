package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.payment.service.result.PreparedRegularPayment;
import com.chapchap.subscription.domain.subscription.service.NextSubscriptionPeriodPreparationService;
import com.chapchap.subscription.domain.subscription.service.PreparedNextSubscriptionPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegularPaymentPreparationServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final LocalDateTime REFERENCE_AT = LocalDateTime.of(2026, 9, 30, 9, 0);

    @Mock private NextSubscriptionPeriodPreparationService nextPeriodPreparationService;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private OrderRepository orderRepository;
    @InjectMocks private RegularPaymentPreparationService service;

    @Test
    void 오전_정기결제는_다음기간_금액으로_거래를_한번만_생성한다() {
        when(nextPeriodPreparationService.prepareIfDue(1L, TODAY, REFERENCE_AT))
            .thenReturn(Optional.of(nextPeriod()));
        when(transactionRepository.findByBusinessDeduplicationKey("PAYMENT:REGULAR:2"))
            .thenReturn(Optional.empty());
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 10L);
            return transaction;
        });

        PreparedRegularPayment prepared = service.prepareInitial(1L, TODAY, REFERENCE_AT).orElseThrow();

        assertThat(prepared.paymentRequired()).isTrue();
        assertThat(prepared.paymentTransactionId()).isEqualTo(10L);
        assertThat(prepared.status()).isEqualTo(PaymentTransactionStatus.PROCESSING);
        assertThat(prepared.allocations()).hasSize(2);
    }

    @Test
    void 이미_정기결제_거래가_있으면_외부결제를_다시_요청하지_않는다() {
        PreparedNextSubscriptionPeriod next = nextPeriod();
        PaymentTransaction existing = regularTransaction("request-1");
        when(nextPeriodPreparationService.prepareIfDue(1L, TODAY, REFERENCE_AT))
            .thenReturn(Optional.of(next));
        when(transactionRepository.findByBusinessDeduplicationKey("PAYMENT:REGULAR:2"))
            .thenReturn(Optional.of(existing));

        PreparedRegularPayment prepared = service.prepareInitial(1L, TODAY, REFERENCE_AT).orElseThrow();

        assertThat(prepared.paymentRequired()).isFalse();
        assertThat(prepared.paymentTransactionId()).isEqualTo(10L);
    }

    @Test
    void 오후_재시도는_동일거래에_새_외부요청키를_설정하고_주문금액을_재사용한다() {
        PaymentTransaction transaction = regularTransaction("request-1");
        transaction.waitForRegularPaymentRetry();
        Order first = order(100L, 40_000L);
        Order second = order(101L, 60_000L);
        when(transactionRepository.findWithLockById(10L)).thenReturn(Optional.of(transaction));
        when(orderRepository.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of(first, second));

        PreparedRegularPayment prepared = service.prepareRetry(10L);

        assertThat(prepared.paymentRequired()).isTrue();
        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.PROCESSING);
        assertThat(transaction.getExternalRequestIdempotencyKey()).startsWith("REGULAR-PAYMENT-13-");
        assertThat(prepared.allocations()).extracting(PaymentAllocationCommand::allocationAmount)
            .containsExactly(40_000L, 60_000L);
    }

    @Test
    void 오후_재시도_대상은_오늘_오전에_처리를_시작한_대기거래로_제한한다() {
        PaymentTransaction transaction = regularTransaction("request-1");
        when(transactionRepository
            .findAllByStatusAndProcessingReferenceAtGreaterThanEqualAndProcessingReferenceAtLessThanOrderByIdAsc(
                PaymentTransactionStatus.RETRY_WAITING,
                TODAY.atStartOfDay(),
                TODAY.plusDays(1).atStartOfDay()
        )).thenReturn(List.of(transaction));

        assertThat(service.findRetryWaitingTransactionIds(TODAY)).containsExactly(10L);

        verify(transactionRepository)
            .findAllByStatusAndProcessingReferenceAtGreaterThanEqualAndProcessingReferenceAtLessThanOrderByIdAsc(
                PaymentTransactionStatus.RETRY_WAITING,
                TODAY.atStartOfDay(),
                TODAY.plusDays(1).atStartOfDay()
        );
    }

    private PreparedNextSubscriptionPeriod nextPeriod() {
        return new PreparedNextSubscriptionPeriod(
            5L, 1L, 2L, TODAY.plusDays(1), TODAY.plusDays(28), REFERENCE_AT, 100_000L,
            List.of(new PaymentAllocationCommand(100L, 40_000L), new PaymentAllocationCommand(101L, 60_000L))
        );
    }

    private PaymentTransaction regularTransaction(String requestKey) {
        PaymentTransaction transaction = PaymentTransaction.createRegularPayment(
            5L, 1L, 2L, 100_000L, REFERENCE_AT, TODAY.plusDays(1), TODAY.plusDays(28),
            requestKey, REFERENCE_AT
        );
        ReflectionTestUtils.setField(transaction, "id", 10L);
        return transaction;
    }

    private Order order(Long id, Long amount) {
        Order order = org.mockito.Mockito.mock(Order.class);
        when(order.getId()).thenReturn(id);
        when(order.getActualAllocatedAmount()).thenReturn(amount);
        return order;
    }
}
