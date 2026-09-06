package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatusHistory;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
import com.chapchap.subscription.global.kafka.auth.AuthSubscriptionStatusPublisher;
import com.chapchap.subscription.global.kafka.customer.CustomerSubscriptionNotificationPublisher;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTerminationServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate END_DATE = TODAY.minusDays(1);
    private static final LocalDateTime ENDED_AT = LocalDateTime.of(2026, 10, 5, 0, 1);

    @Mock private SubscriptionPeriodRepository periodRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionStatusHistoryRepository historyRepository;
    @Mock private AuthSubscriptionStatusPublisher authPublisher;
    @Mock private KstReferenceTimeProvider timeProvider;
    @Mock private CustomerSubscriptionNotificationPublisher customerPublisher;

    private SubscriptionTerminationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionTerminationService(
            periodRepository, subscriptionRepository, historyRepository,
            authPublisher, timeProvider, customerPublisher
        );
        when(timeProvider.now()).thenReturn(ENDED_AT);
    }

    @Test
    void 해지_예정_구독은_마지막_이용기간과_함께_종료한다() {
        Subscription subscription = inProgressSubscription();
        subscription.scheduleCancellation(ENDED_AT.minusDays(7));
        SubscriptionPeriod current = inProgressPeriod(2L, 1, END_DATE.minusDays(27));
        terminationCandidate(subscription, current);

        service.terminateDueSubscriptions(TODAY);

        assertThat(current.getStatus()).isEqualTo(SubscriptionPeriodStatus.ENDED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ENDED);
        assertTerminationEffects(subscription, SubscriptionStatus.CANCELLATION_SCHEDULED,
            "CANCELLATION_PERIOD_ENDED", "CUSTOMER_CANCELLATION");
    }

    @Test
    void 다음_기간_결제_최종_실패면_현재_이용기간과_구독을_함께_종료한다() {
        Subscription subscription = inProgressSubscription();
        SubscriptionPeriod current = inProgressPeriod(2L, 1, END_DATE.minusDays(27));
        SubscriptionPeriod failedNext = awaitingPeriod(3L, 2, TODAY);
        failedNext.markPaymentFailed();
        terminationCandidate(subscription, current);
        when(periodRepository.findTopBySubscriptionIdOrderByPeriodSequenceDesc(1L))
            .thenReturn(Optional.of(failedNext));

        service.terminateDueSubscriptions(TODAY);

        assertThat(current.getStatus()).isEqualTo(SubscriptionPeriodStatus.ENDED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ENDED);
        assertThat(failedNext.getStatus()).isEqualTo(SubscriptionPeriodStatus.PAYMENT_FAILED);
        assertTerminationEffects(subscription, SubscriptionStatus.IN_PROGRESS,
            "REGULAR_PAYMENT_FINAL_FAILURE", "REGULAR_PAYMENT_FINAL_FAILURE");
    }

    @Test
    void 종료_조건이_없는_이용중_구독은_기간과_구독을_변경하지_않는다() {
        Subscription subscription = inProgressSubscription();
        SubscriptionPeriod current = inProgressPeriod(2L, 1, END_DATE.minusDays(27));
        terminationCandidate(subscription, current);
        when(periodRepository.findTopBySubscriptionIdOrderByPeriodSequenceDesc(1L))
            .thenReturn(Optional.of(current));

        service.terminateDueSubscriptions(TODAY);

        assertThat(current.getStatus()).isEqualTo(SubscriptionPeriodStatus.IN_PROGRESS);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.IN_PROGRESS);
        verify(historyRepository, never()).save(any());
        verify(authPublisher, never()).publishAfterCommit(any(), any(), any(), any());
        verify(customerPublisher, never()).publishEndedAfterCommit(any(), any(), any());
    }

    @Test
    void 이미_종료된_대상을_다시_조회해도_이력과_이벤트를_중복_생성하지_않는다() {
        Subscription subscription = inProgressSubscription();
        subscription.scheduleCancellation(ENDED_AT.minusDays(7));
        SubscriptionPeriod current = inProgressPeriod(2L, 1, END_DATE.minusDays(27));
        terminationCandidate(subscription, current);

        service.terminateDueSubscriptions(TODAY);
        service.terminateDueSubscriptions(TODAY);

        verify(historyRepository, times(1)).save(any());
        verify(authPublisher, times(1)).publishAfterCommit(any(), any(), any(), any());
        verify(customerPublisher, times(1)).publishEndedAfterCommit(any(), any(), any());
    }

    private void terminationCandidate(Subscription subscription, SubscriptionPeriod current) {
        when(periodRepository.findAllByStatusAndPeriodEndDate(SubscriptionPeriodStatus.IN_PROGRESS, END_DATE))
            .thenReturn(List.of(current));
        when(subscriptionRepository.findWithLockById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(periodRepository.findWithLockById(current.getId())).thenReturn(Optional.of(current));
    }

    private void assertTerminationEffects(
        Subscription subscription,
        SubscriptionStatus previousStatus,
        String historyReason,
        String eventReason
    ) {
        ArgumentCaptor<SubscriptionStatusHistory> history = ArgumentCaptor.forClass(SubscriptionStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getPreviousStatus()).isEqualTo(previousStatus);
        assertThat(history.getValue().getNextStatus()).isEqualTo(SubscriptionStatus.ENDED);
        assertThat(history.getValue().getChangeReason()).isEqualTo(historyReason);
        verify(authPublisher).publishAfterCommit(subscription, previousStatus, SubscriptionStatus.ENDED, ENDED_AT);
        verify(customerPublisher).publishEndedAfterCommit(subscription, eventReason, ENDED_AT);
    }

    private Subscription inProgressSubscription() {
        Subscription subscription = Subscription.create(10L);
        ReflectionTestUtils.setField(subscription, "id", 1L);
        subscription.markScheduled();
        subscription.startFirstPeriod();
        return subscription;
    }

    private SubscriptionPeriod inProgressPeriod(Long id, int sequence, LocalDate startDate) {
        SubscriptionPeriod period = awaitingPeriod(id, sequence, startDate);
        period.markScheduled();
        period.start();
        return period;
    }

    private SubscriptionPeriod awaitingPeriod(Long id, int sequence, LocalDate startDate) {
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(
            1L, sequence, startDate, ENDED_AT.minusDays(30)
        );
        ReflectionTestUtils.setField(period, "id", id);
        return period;
    }
}
