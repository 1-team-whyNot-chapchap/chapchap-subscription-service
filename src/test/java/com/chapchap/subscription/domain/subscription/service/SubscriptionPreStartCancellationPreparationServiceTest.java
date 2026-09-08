package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.subscription.entity.*;
import com.chapchap.subscription.domain.subscription.repository.*;
import com.chapchap.subscription.global.exception.subscription.SubscriptionKafkaDeliveryCompletedException;
import com.chapchap.subscription.global.exception.subscription.SubscriptionPreStartCancellationDeadlinePassedException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionPreStartCancellationPreparationServiceTest {
    @Test
    void 시작전날_14시_전이고_Kafka미전달이면_첫시작취소를_준비한다() {
        SubscriptionRepository subscriptions=mock(SubscriptionRepository.class); SubscriptionPeriodRepository periods=mock(SubscriptionPeriodRepository.class); PaymentTransactionRepository payments=mock(PaymentTransactionRepository.class); OrderRepository orders=mock(OrderRepository.class); KstReferenceTimeProvider time=mock(KstReferenceTimeProvider.class);
        SubscriptionPreStartCancellationPreparationService service=new SubscriptionPreStartCancellationPreparationService(subscriptions,periods,payments,orders,time);
        Subscription subscription=Subscription.create(10L); ReflectionTestUtils.setField(subscription,"id",1L); subscription.markScheduled();
        SubscriptionPeriod period=SubscriptionPeriod.createAwaitingConfirmation(1L,1,LocalDate.of(2026,9,10),LocalDateTime.now()); period.markScheduled(); ReflectionTestUtils.setField(period,"id",2L);
        when(subscriptions.findWithLockByUserId(10L)).thenReturn(Optional.of(subscription)); when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(1L,SubscriptionPeriodStatus.SCHEDULED)).thenReturn(Optional.of(period)); when(time.now()).thenReturn(LocalDateTime.of(2026,9,9,13,59,59)); when(orders.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of());
        SubscriptionCancellationPreparation result=service.prepare(10L);
        assertThat(result.cancellationType()).isEqualTo(SubscriptionCancellationType.CANCELLATION_BEFORE_START);
    }

    @Test
    void 재신청_기간은_순번이_2이상이어도_시작취소를_준비한다() {
        SubscriptionRepository subscriptions=mock(SubscriptionRepository.class); SubscriptionPeriodRepository periods=mock(SubscriptionPeriodRepository.class); PaymentTransactionRepository payments=mock(PaymentTransactionRepository.class); OrderRepository orders=mock(OrderRepository.class); KstReferenceTimeProvider time=mock(KstReferenceTimeProvider.class);
        SubscriptionPreStartCancellationPreparationService service=new SubscriptionPreStartCancellationPreparationService(subscriptions,periods,payments,orders,time);
        Subscription subscription=Subscription.create(10L); ReflectionTestUtils.setField(subscription,"id",1L); subscription.markScheduled();
        SubscriptionPeriod period=SubscriptionPeriod.createAwaitingConfirmation(1L,2,LocalDate.of(2026,9,10),LocalDateTime.now()); period.markScheduled(); ReflectionTestUtils.setField(period,"id",2L);
        when(subscriptions.findWithLockByUserId(10L)).thenReturn(Optional.of(subscription)); when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(1L,SubscriptionPeriodStatus.SCHEDULED)).thenReturn(Optional.of(period)); when(time.now()).thenReturn(LocalDateTime.of(2026,9,9,13,59,59)); when(orders.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of());

        SubscriptionCancellationPreparation result=service.prepare(10L);

        assertThat(result.cancellationType()).isEqualTo(SubscriptionCancellationType.CANCELLATION_BEFORE_START);
        assertThat(result.targetPeriodId()).isEqualTo(2L);
    }

    @Test
    void 이용중_구독의_다음_시작예정_기간은_다음기간_전액취소를_준비한다() {
        SubscriptionRepository subscriptions=mock(SubscriptionRepository.class); SubscriptionPeriodRepository periods=mock(SubscriptionPeriodRepository.class); PaymentTransactionRepository payments=mock(PaymentTransactionRepository.class); OrderRepository orders=mock(OrderRepository.class); KstReferenceTimeProvider time=mock(KstReferenceTimeProvider.class);
        SubscriptionPreStartCancellationPreparationService service=new SubscriptionPreStartCancellationPreparationService(subscriptions,periods,payments,orders,time);
        Subscription subscription=Subscription.create(10L); ReflectionTestUtils.setField(subscription,"id",1L); subscription.markScheduled(); subscription.startFirstPeriod();
        SubscriptionPeriod period=SubscriptionPeriod.createAwaitingConfirmation(1L,2,LocalDate.of(2026,9,10),LocalDateTime.now()); period.markScheduled(); ReflectionTestUtils.setField(period,"id",2L);
        when(subscriptions.findWithLockByUserId(10L)).thenReturn(Optional.of(subscription)); when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(1L,SubscriptionPeriodStatus.SCHEDULED)).thenReturn(Optional.of(period)); when(time.now()).thenReturn(LocalDateTime.of(2026,9,9,13,59,59)); when(orders.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of());

        SubscriptionCancellationPreparation result=service.prepare(10L);

        assertThat(result.cancellationType()).isEqualTo(SubscriptionCancellationType.NEXT_PERIOD_FULL_CANCELLATION);
        assertThat(result.targetPeriodId()).isEqualTo(2L);
    }

    @Test
    void 시작전날_14시부터는_시작취소를_차단한다() {
        SubscriptionRepository subscriptions=mock(SubscriptionRepository.class); SubscriptionPeriodRepository periods=mock(SubscriptionPeriodRepository.class); KstReferenceTimeProvider time=mock(KstReferenceTimeProvider.class);
        SubscriptionPreStartCancellationPreparationService service=new SubscriptionPreStartCancellationPreparationService(subscriptions,periods,mock(PaymentTransactionRepository.class),mock(OrderRepository.class),time);
        Subscription subscription=Subscription.create(10L); ReflectionTestUtils.setField(subscription,"id",1L); subscription.markScheduled(); SubscriptionPeriod period=SubscriptionPeriod.createAwaitingConfirmation(1L,1,LocalDate.of(2026,9,10),LocalDateTime.now()); period.markScheduled();
        when(subscriptions.findWithLockByUserId(10L)).thenReturn(Optional.of(subscription)); when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(1L,SubscriptionPeriodStatus.SCHEDULED)).thenReturn(Optional.of(period)); when(time.now()).thenReturn(LocalDateTime.of(2026,9,9,14,0));
        assertThatThrownBy(() -> service.prepare(10L)).isInstanceOf(SubscriptionPreStartCancellationDeadlinePassedException.class);
    }

    @Test
    void Kafka전달완료_주문이_있으면_시작취소를_차단한다() {
        SubscriptionRepository subscriptions=mock(SubscriptionRepository.class); SubscriptionPeriodRepository periods=mock(SubscriptionPeriodRepository.class); OrderRepository orders=mock(OrderRepository.class); KstReferenceTimeProvider time=mock(KstReferenceTimeProvider.class);
        SubscriptionPreStartCancellationPreparationService service=new SubscriptionPreStartCancellationPreparationService(subscriptions,periods,mock(PaymentTransactionRepository.class),orders,time);
        Subscription subscription=Subscription.create(10L); ReflectionTestUtils.setField(subscription,"id",1L); subscription.markScheduled(); SubscriptionPeriod period=SubscriptionPeriod.createAwaitingConfirmation(1L,1,LocalDate.of(2026,9,10),LocalDateTime.now()); period.markScheduled(); ReflectionTestUtils.setField(period,"id",2L);
        when(subscriptions.findWithLockByUserId(10L)).thenReturn(Optional.of(subscription)); when(periods.findTopBySubscriptionIdAndStatusOrderByPeriodSequenceDesc(1L,SubscriptionPeriodStatus.SCHEDULED)).thenReturn(Optional.of(period)); when(time.now()).thenReturn(LocalDateTime.of(2026,9,9,13,0)); when(orders.existsBySubscriptionPeriodIdAndKafkaDeliveryStatus(2L,OrderKafkaDeliveryStatus.COMPLETED)).thenReturn(true);
        assertThatThrownBy(() -> service.prepare(10L)).isInstanceOf(SubscriptionKafkaDeliveryCompletedException.class);
    }
}
