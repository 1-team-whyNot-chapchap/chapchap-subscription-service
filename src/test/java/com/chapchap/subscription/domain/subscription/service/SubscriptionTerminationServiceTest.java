package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.*;
import com.chapchap.subscription.domain.subscription.repository.*;
import com.chapchap.subscription.global.kafka.auth.AuthSubscriptionStatusPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SubscriptionTerminationServiceTest {
    @Test
    void 해지예정_구독은_현재기간_종료_다음날_종료되고_Auth에_전달한다() {
        SubscriptionPeriodRepository periods = mock(SubscriptionPeriodRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SubscriptionStatusHistoryRepository histories = mock(SubscriptionStatusHistoryRepository.class);
        AuthSubscriptionStatusPublisher auth = mock(AuthSubscriptionStatusPublisher.class);
        KstReferenceTimeProvider time = mock(KstReferenceTimeProvider.class);
        var customerPublisher = mock(com.chapchap.subscription.global.kafka.customer.CustomerSubscriptionNotificationPublisher.class);
        SubscriptionTerminationService service = new SubscriptionTerminationService(periods, subscriptions, histories, auth, time,
            customerPublisher);
        LocalDate today = LocalDate.of(2026, 10, 5);
        Subscription subscription = Subscription.create(10L); ReflectionTestUtils.setField(subscription, "id", 1L); ReflectionTestUtils.setField(subscription, "status", SubscriptionStatus.CANCELLATION_SCHEDULED);
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(1L, 1, today.minusDays(28), LocalDateTime.of(2026, 9, 1, 0, 0)); ReflectionTestUtils.setField(period, "id", 2L); period.markScheduled(); period.start();
        LocalDateTime endedAt = LocalDateTime.of(2026, 10, 5, 0, 1);
        when(periods.findAllByStatusAndPeriodEndDate(SubscriptionPeriodStatus.IN_PROGRESS, today.minusDays(1))).thenReturn(List.of(period));
        when(periods.findWithLockById(2L)).thenReturn(Optional.of(period)); when(subscriptions.findWithLockById(1L)).thenReturn(Optional.of(subscription)); when(time.now()).thenReturn(endedAt);

        service.terminateDueSubscriptions(today);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ENDED);
        verify(histories).save(any(SubscriptionStatusHistory.class));
        verify(auth).publishAfterCommit(subscription, SubscriptionStatus.CANCELLATION_SCHEDULED, SubscriptionStatus.ENDED, endedAt);
        verify(customerPublisher).publishEndedAfterCommit(subscription, "CUSTOMER_CANCELLATION", endedAt);
    }
}
