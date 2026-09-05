package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionStatusHistoryRepository;
import com.chapchap.subscription.global.exception.payment.PaymentTransactionProcessingException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SubscriptionCancellationPreparationServiceTest {
    @Test
    void 이용중_구독은_일반해지_예정으로_전환하고_이력을_저장한다() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class); PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class); SubscriptionStatusHistoryRepository histories = mock(SubscriptionStatusHistoryRepository.class); KstReferenceTimeProvider time = mock(KstReferenceTimeProvider.class);
        SubscriptionCancellationPreparationService service = new SubscriptionCancellationPreparationService(subscriptions, payments, histories, time);
        Subscription subscription = Subscription.create(10L); ReflectionTestUtils.setField(subscription, "id", 1L); subscription.markScheduled(); subscription.startFirstPeriod();
        when(subscriptions.findWithLockByUserId(10L)).thenReturn(Optional.of(subscription)); when(time.now()).thenReturn(LocalDateTime.of(2026, 9, 7, 12, 0));

        service.cancelRegular(10L);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLATION_SCHEDULED);
        assertThat(subscription.getCancellationRequestedAt()).isEqualTo(LocalDateTime.of(2026, 9, 7, 12, 0));
        verify(histories).save(any());
    }

    @Test
    void 결제처리중이면_일반해지를_확정하지_않는다() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class); PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class); SubscriptionCancellationPreparationService service = new SubscriptionCancellationPreparationService(subscriptions, payments, mock(SubscriptionStatusHistoryRepository.class), mock(KstReferenceTimeProvider.class));
        Subscription subscription = Subscription.create(10L); ReflectionTestUtils.setField(subscription, "id", 1L); subscription.markScheduled(); subscription.startFirstPeriod();
        when(subscriptions.findWithLockByUserId(10L)).thenReturn(Optional.of(subscription)); when(payments.existsBySubscriptionIdAndStatus(1L, PaymentTransactionStatus.PROCESSING)).thenReturn(true);

        assertThatThrownBy(() -> service.cancelRegular(10L)).isInstanceOf(PaymentTransactionProcessingException.class);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.IN_PROGRESS);
    }
}
