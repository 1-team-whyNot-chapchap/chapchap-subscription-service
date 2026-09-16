package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.*;
import com.chapchap.subscription.domain.subscription.repository.*;
import com.chapchap.subscription.global.kafka.auth.AuthSubscriptionStatusPublisher;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.global.exception.subscription.SubscriptionKafkaDeliveryCompletedException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SubscriptionPreStartCancellationCompletionServiceTest {
    @Test
    void 첫시작취소_성공시_기간과_구독을_시작취소로_확정한다() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class); SubscriptionPeriodRepository periods = mock(SubscriptionPeriodRepository.class); OrderRepository orders = mock(OrderRepository.class); SubscriptionStatusHistoryRepository histories = mock(SubscriptionStatusHistoryRepository.class);
        AuthSubscriptionStatusPublisher authPublisher = mock(AuthSubscriptionStatusPublisher.class);
        RefundRepository refunds = mock(RefundRepository.class);
        KstReferenceTimeProvider time = mock(KstReferenceTimeProvider.class);
        var customerPublisher = mock(com.chapchap.subscription.global.kafka.customer.CustomerSubscriptionNotificationPublisher.class);
        var customerRefundPublisher = mock(com.chapchap.subscription.global.kafka.customer.CustomerRefundEventPublisher.class);
        SubscriptionPreStartCancellationCompletionService service = new SubscriptionPreStartCancellationCompletionService(subscriptions, periods, orders, histories, authPublisher, refunds,
            time, customerPublisher, customerRefundPublisher);
        Subscription subscription = Subscription.create(1L); ReflectionTestUtils.setField(subscription, "id", 1L); subscription.markScheduled();
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(1L, 1, LocalDate.of(2026, 9, 9), LocalDateTime.of(2026, 9, 1, 0, 0)); period.markScheduled(); ReflectionTestUtils.setField(period, "id", 2L);
        Order order = mock(Order.class);
        Refund refund = Refund.createPeriodCancellation(1L, 2L, com.chapchap.subscription.domain.payment.entity.RefundType.CANCELLATION_BEFORE_START, 10_000L);
        refund.addSuccessfulAmount(10_000L, LocalDateTime.of(2026, 9, 8, 12, 0));
        when(subscriptions.findWithLockById(1L)).thenReturn(Optional.of(subscription)); when(periods.findWithLockById(2L)).thenReturn(Optional.of(period)); when(orders.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of(order)); when(refunds.findBySubscriptionPeriodId(2L)).thenReturn(Optional.of(refund));
        when(time.now()).thenReturn(LocalDateTime.of(2026, 9, 8, 12, 1));
        service.complete(new SubscriptionCancellationPreparation(SubscriptionCancellationType.CANCELLATION_BEFORE_START, 1L, 2L, List.of(), LocalDateTime.of(2026, 9, 8, 12, 0)));
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED_BEFORE_START); assertThat(period.getStatus()).isEqualTo(SubscriptionPeriodStatus.CANCELED_BEFORE_START); verify(order).cancelBeforeStart(); verify(histories).save(any()); verify(authPublisher).publishAfterCommit(subscription, SubscriptionStatus.SCHEDULED, SubscriptionStatus.CANCELED_BEFORE_START, LocalDateTime.of(2026, 9, 8, 12, 0));
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        verify(customerRefundPublisher).publishCompletedAfterCommit(refund, 1L, LocalDateTime.of(2026, 9, 8, 12, 0));
        verify(customerPublisher).publishCancellationConfirmedAfterCommit(
            subscription, "CANCELLATION_BEFORE_START", LocalDateTime.of(2026, 9, 8, 12, 0),
            LocalDateTime.of(2026, 9, 8, 12, 1), null, "COMPLETED", LocalDateTime.of(2026, 9, 8, 12, 1)
        );
    }

    @Test
    void 최종확정이_실패하면_환불완료로_전환하거나_환불이벤트를_발행하지_않는다() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class); SubscriptionPeriodRepository periods = mock(SubscriptionPeriodRepository.class); OrderRepository orders = mock(OrderRepository.class); SubscriptionStatusHistoryRepository histories = mock(SubscriptionStatusHistoryRepository.class);
        AuthSubscriptionStatusPublisher authPublisher = mock(AuthSubscriptionStatusPublisher.class);
        RefundRepository refunds = mock(RefundRepository.class);
        KstReferenceTimeProvider time = mock(KstReferenceTimeProvider.class);
        var customerPublisher = mock(com.chapchap.subscription.global.kafka.customer.CustomerSubscriptionNotificationPublisher.class);
        var customerRefundPublisher = mock(com.chapchap.subscription.global.kafka.customer.CustomerRefundEventPublisher.class);
        SubscriptionPreStartCancellationCompletionService service = new SubscriptionPreStartCancellationCompletionService(subscriptions, periods, orders, histories, authPublisher, refunds,
            time, customerPublisher, customerRefundPublisher);
        Subscription subscription = Subscription.create(1L); ReflectionTestUtils.setField(subscription, "id", 1L); subscription.markScheduled();
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(1L, 1, LocalDate.of(2026, 9, 9), LocalDateTime.of(2026, 9, 1, 0, 0)); period.markScheduled(); ReflectionTestUtils.setField(period, "id", 2L);
        Refund refund = Refund.createPeriodCancellation(1L, 2L, com.chapchap.subscription.domain.payment.entity.RefundType.CANCELLATION_BEFORE_START, 10_000L);
        refund.addSuccessfulAmount(10_000L, LocalDateTime.of(2026, 9, 8, 12, 0));
        when(subscriptions.findWithLockById(1L)).thenReturn(Optional.of(subscription)); when(periods.findWithLockById(2L)).thenReturn(Optional.of(period)); when(refunds.findBySubscriptionPeriodId(2L)).thenReturn(Optional.of(refund));
        when(orders.existsBySubscriptionPeriodIdAndKafkaDeliveryStatus(2L, com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus.COMPLETED)).thenReturn(true);

        assertThatThrownBy(() -> service.complete(new SubscriptionCancellationPreparation(
            SubscriptionCancellationType.CANCELLATION_BEFORE_START, 1L, 2L, List.of(), LocalDateTime.of(2026, 9, 8, 12, 0)
        ))).isInstanceOf(SubscriptionKafkaDeliveryCompletedException.class);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FINALIZATION_PENDING);
        verifyNoInteractions(customerRefundPublisher);
    }
}
