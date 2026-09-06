package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.subscription.entity.*;
import com.chapchap.subscription.domain.subscription.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionRetryStopCancellationServiceTest {
    @Test
    void 재시도대기_거래를_중단하고_다음기간과_구독을_취소예정으로_바꾼다() {
        SubscriptionRepository subscriptions=mock(SubscriptionRepository.class); SubscriptionPeriodRepository periods=mock(SubscriptionPeriodRepository.class); PaymentTransactionRepository payments=mock(PaymentTransactionRepository.class); OrderRepository orders=mock(OrderRepository.class); SubscriptionStatusHistoryRepository histories=mock(SubscriptionStatusHistoryRepository.class); KstReferenceTimeProvider time=mock(KstReferenceTimeProvider.class);
        SubscriptionRetryStopCancellationService service=new SubscriptionRetryStopCancellationService(subscriptions,periods,payments,orders,histories,time);
        Subscription subscription=Subscription.create(10L); ReflectionTestUtils.setField(subscription,"id",1L); subscription.markScheduled(); subscription.startFirstPeriod();
        SubscriptionPeriod period=SubscriptionPeriod.createAwaitingConfirmation(1L,2,LocalDate.of(2026,9,10),LocalDateTime.now()); ReflectionTestUtils.setField(period,"id",2L);
        PaymentTransaction transaction=mock(PaymentTransaction.class); when(transaction.getSubscriptionId()).thenReturn(1L); when(transaction.getSubscriptionPeriodId()).thenReturn(2L); when(transaction.getStatus()).thenReturn(PaymentTransactionStatus.RETRY_WAITING);
        Order order=mock(Order.class);
        when(subscriptions.findWithLockByUserId(10L)).thenReturn(Optional.of(subscription)); when(time.now()).thenReturn(LocalDateTime.of(2026,9,9,12,0)); when(payments.findTopWithLockBySubscriptionIdAndStatusOrderByOccurredAtDescIdDesc(1L,PaymentTransactionStatus.RETRY_WAITING)).thenReturn(Optional.of(transaction)); when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(1L,SubscriptionPeriodStatus.AWAITING_CONFIRMATION)).thenReturn(Optional.of(period)); when(orders.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of(order));
        service.cancel(10L);
        verify(transaction).stopRetry(); verify(order).cancelAwaitingRegularPayment(); verify(histories).save(any()); assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLATION_SCHEDULED); assertThat(period.getStatus()).isEqualTo(SubscriptionPeriodStatus.CANCELED_BEFORE_START);
    }
}
