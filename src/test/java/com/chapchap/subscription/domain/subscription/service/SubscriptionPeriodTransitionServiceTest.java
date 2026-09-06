package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriod;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionPeriodStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatusHistory;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
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
class SubscriptionPeriodTransitionServiceTest {
    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 7);
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 9, 7, 0, 1);

    @Mock private SubscriptionPeriodRepository periodRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionStatusHistoryRepository historyRepository;
    @Mock private KstReferenceTimeProvider timeProvider;

    private SubscriptionPeriodTransitionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPeriodTransitionService(
            periodRepository, subscriptionRepository, historyRepository, timeProvider
        );
        when(timeProvider.now()).thenReturn(CHANGED_AT);
    }

    @Test
    void 첫_이용기간은_구독과_함께_이용중으로_전환하고_이력을_남긴다() {
        Subscription subscription = scheduledSubscription();
        SubscriptionPeriod first = scheduledPeriod(2L, 1, START_DATE);
        candidates(first);
        when(subscriptionRepository.findWithLockById(1L)).thenReturn(Optional.of(subscription));
        when(periodRepository.findWithLockById(2L)).thenReturn(Optional.of(first));

        service.transitionScheduledPeriods(START_DATE);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.IN_PROGRESS);
        assertThat(first.getStatus()).isEqualTo(SubscriptionPeriodStatus.IN_PROGRESS);
        ArgumentCaptor<SubscriptionStatusHistory> captor = ArgumentCaptor.forClass(SubscriptionStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousStatus()).isEqualTo(SubscriptionStatus.SCHEDULED);
        assertThat(captor.getValue().getNextStatus()).isEqualTo(SubscriptionStatus.IN_PROGRESS);
        assertThat(captor.getValue().getChangeReason()).isEqualTo("FIRST_PERIOD_STARTED");
        assertThat(captor.getValue().getChangedAt()).isEqualTo(CHANGED_AT);
    }

    @Test
    void 다음_이용기간은_직전_기간을_종료하고_시작하되_구독_상태와_이력은_바꾸지_않는다() {
        Subscription subscription = inProgressSubscription();
        SubscriptionPeriod previous = inProgressPeriod(2L, 1, START_DATE.minusDays(28));
        SubscriptionPeriod next = scheduledPeriod(3L, 2, START_DATE);
        candidates(next);
        when(subscriptionRepository.findWithLockById(1L)).thenReturn(Optional.of(subscription));
        when(periodRepository.findWithLockById(3L)).thenReturn(Optional.of(next));
        when(periodRepository.findWithLockBySubscriptionIdAndPeriodSequence(1L, 1))
            .thenReturn(Optional.of(previous));

        service.transitionScheduledPeriods(START_DATE);

        assertThat(previous.getStatus()).isEqualTo(SubscriptionPeriodStatus.ENDED);
        assertThat(next.getStatus()).isEqualTo(SubscriptionPeriodStatus.IN_PROGRESS);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.IN_PROGRESS);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void 직전_기간과_다음_기간의_날짜가_연속되지_않으면_전환하지_않는다() {
        Subscription subscription = inProgressSubscription();
        SubscriptionPeriod previous = inProgressPeriod(2L, 1, START_DATE.minusDays(29));
        SubscriptionPeriod next = scheduledPeriod(3L, 2, START_DATE);
        candidates(next);
        when(subscriptionRepository.findWithLockById(1L)).thenReturn(Optional.of(subscription));
        when(periodRepository.findWithLockById(3L)).thenReturn(Optional.of(next));
        when(periodRepository.findWithLockBySubscriptionIdAndPeriodSequence(1L, 1))
            .thenReturn(Optional.of(previous));

        service.transitionScheduledPeriods(START_DATE);

        assertThat(previous.getStatus()).isEqualTo(SubscriptionPeriodStatus.IN_PROGRESS);
        assertThat(next.getStatus()).isEqualTo(SubscriptionPeriodStatus.SCHEDULED);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void 직전_순번의_이용기간이_없으면_다음_기간을_시작하지_않는다() {
        Subscription subscription = inProgressSubscription();
        SubscriptionPeriod next = scheduledPeriod(3L, 2, START_DATE);
        candidates(next);
        when(subscriptionRepository.findWithLockById(1L)).thenReturn(Optional.of(subscription));
        when(periodRepository.findWithLockById(3L)).thenReturn(Optional.of(next));
        when(periodRepository.findWithLockBySubscriptionIdAndPeriodSequence(1L, 1))
            .thenReturn(Optional.empty());

        service.transitionScheduledPeriods(START_DATE);

        assertThat(next.getStatus()).isEqualTo(SubscriptionPeriodStatus.SCHEDULED);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void 후보_조회_뒤_이미_처리된_다음_기간은_다시_전환하지_않는다() {
        Subscription subscription = inProgressSubscription();
        SubscriptionPeriod next = scheduledPeriod(3L, 2, START_DATE);
        next.start();
        candidates(next);
        when(subscriptionRepository.findWithLockById(1L)).thenReturn(Optional.of(subscription));
        when(periodRepository.findWithLockById(3L)).thenReturn(Optional.of(next));

        service.transitionScheduledPeriods(START_DATE);

        assertThat(next.getStatus()).isEqualTo(SubscriptionPeriodStatus.IN_PROGRESS);
        verify(periodRepository, never()).findWithLockBySubscriptionIdAndPeriodSequence(any(), any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void 해지_예정_구독의_다음_기간은_시작하지_않는다() {
        Subscription subscription = inProgressSubscription();
        subscription.scheduleCancellation(CHANGED_AT.minusDays(1));
        SubscriptionPeriod next = scheduledPeriod(3L, 2, START_DATE);
        candidates(next);
        when(subscriptionRepository.findWithLockById(1L)).thenReturn(Optional.of(subscription));
        when(periodRepository.findWithLockById(3L)).thenReturn(Optional.of(next));

        service.transitionScheduledPeriods(START_DATE);

        assertThat(next.getStatus()).isEqualTo(SubscriptionPeriodStatus.SCHEDULED);
        verify(periodRepository, never()).findWithLockBySubscriptionIdAndPeriodSequence(any(), any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void 이미_처리된_기간을_다시_조회해도_이력과_전이를_중복_처리하지_않는다() {
        Subscription subscription = scheduledSubscription();
        SubscriptionPeriod first = scheduledPeriod(2L, 1, START_DATE);
        candidates(first);
        when(subscriptionRepository.findWithLockById(1L)).thenReturn(Optional.of(subscription));
        when(periodRepository.findWithLockById(2L)).thenReturn(Optional.of(first));

        service.transitionScheduledPeriods(START_DATE);
        service.transitionScheduledPeriods(START_DATE);

        assertThat(first.getStatus()).isEqualTo(SubscriptionPeriodStatus.IN_PROGRESS);
        verify(historyRepository, times(1)).save(any());
    }

    private Subscription scheduledSubscription() {
        Subscription subscription = Subscription.create(10L);
        ReflectionTestUtils.setField(subscription, "id", 1L);
        subscription.markScheduled();
        return subscription;
    }

    private Subscription inProgressSubscription() {
        Subscription subscription = scheduledSubscription();
        subscription.startFirstPeriod();
        return subscription;
    }

    private SubscriptionPeriod scheduledPeriod(Long id, int sequence, LocalDate startDate) {
        SubscriptionPeriod period = SubscriptionPeriod.createAwaitingConfirmation(
            1L, sequence, startDate, CHANGED_AT.minusDays(1)
        );
        ReflectionTestUtils.setField(period, "id", id);
        period.markScheduled();
        return period;
    }

    private SubscriptionPeriod inProgressPeriod(Long id, int sequence, LocalDate startDate) {
        SubscriptionPeriod period = scheduledPeriod(id, sequence, startDate);
        period.start();
        return period;
    }

    private void candidates(SubscriptionPeriod... periods) {
        when(periodRepository.findAllByStatusAndPeriodStartDate(
            SubscriptionPeriodStatus.SCHEDULED, START_DATE
        )).thenReturn(List.of(periods));
    }
}
