package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.*;
import com.chapchap.subscription.domain.subscription.repository.*;
import com.chapchap.subscription.global.kafka.auth.AuthSubscriptionStatusPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SubscriptionPreStartCancellationCompletionServiceTest {
    @Test
    void 첫시작취소_성공시_기간과_구독을_시작취소로_확정한다() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class); SubscriptionPeriodRepository periods = mock(SubscriptionPeriodRepository.class); OrderRepository orders = mock(OrderRepository.class); SubscriptionStatusHistoryRepository histories = mock(SubscriptionStatusHistoryRepository.class);
        AuthSubscriptionStatusPublisher authPublisher = mock(AuthSubscriptionStatusPublisher.class);
        SubscriptionPreStartCancellationCompletionService service = new SubscriptionPreStartCancellationCompletionService(subscriptions, periods, orders, histories, authPublisher);
        Subscription subscription = Subscription.create(1L); ReflectionTestUtils.setField(subscription, "id", 1L); subscription.markScheduled();
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(1L, 1, LocalDate.of(2026, 9, 9), LocalDateTime.of(2026, 9, 1, 0, 0)); period.markScheduled(); ReflectionTestUtils.setField(period, "id", 2L);
        Order order = mock(Order.class);
        when(subscriptions.findWithLockById(1L)).thenReturn(Optional.of(subscription)); when(periods.findWithLockById(2L)).thenReturn(Optional.of(period)); when(orders.findAllBySubscriptionPeriodId(2L)).thenReturn(List.of(order));
        service.complete(new SubscriptionCancellationPreparation(SubscriptionCancellationType.CANCELLATION_BEFORE_START, 1L, 2L, List.of(), LocalDateTime.of(2026, 9, 8, 12, 0)));
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED_BEFORE_START); assertThat(period.getStatus()).isEqualTo(SubscriptionPeriodStatus.CANCELED_BEFORE_START); verify(order).cancelBeforeStart(); verify(histories).save(any()); verify(authPublisher).publishAfterCommit(subscription, SubscriptionStatus.SCHEDULED, SubscriptionStatus.CANCELED_BEFORE_START, LocalDateTime.of(2026, 9, 8, 12, 0));
    }
}
