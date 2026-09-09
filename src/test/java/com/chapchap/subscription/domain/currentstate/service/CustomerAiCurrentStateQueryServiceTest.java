package com.chapchap.subscription.domain.currentstate.service;

import com.chapchap.subscription.domain.currentstate.response.CurrentPaymentStateResponse;
import com.chapchap.subscription.domain.currentstate.response.CurrentRefundStateResponse;
import com.chapchap.subscription.domain.currentstate.response.CurrentSubscriptionStateResponse;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAiCurrentStateQueryServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long SUBSCRIPTION_ID = 20L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 9, 7, 9, 0);

    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    private CustomerAiCurrentStateQueryService service;

    @BeforeEach
    void setUp() {
        service = new CustomerAiCurrentStateQueryService(
            paymentTransactionRepository,
            refundRepository,
            subscriptionRepository
        );
    }

    @Test
    void 현재_결제는_원_결제_세_종류만_대상으로_최신_한_건을_반환한다() {
        PaymentTransaction payment = mock(PaymentTransaction.class);
        when(payment.getStatus()).thenReturn(PaymentTransactionStatus.RETRY_WAITING);
        when(payment.getTransactionType()).thenReturn(PaymentTransactionType.REGULAR_PAYMENT);
        when(payment.getTransactionAmount()).thenReturn(12_900L);
        when(payment.getOccurredAt()).thenReturn(OCCURRED_AT);
        when(paymentTransactionRepository.findFirstByUserIdAndTransactionTypeInOrderByOccurredAtDescIdDesc(
            org.mockito.ArgumentMatchers.eq(USER_ID),
            argThat(this::containsOnlyOriginalPaymentTypes)
        )).thenReturn(Optional.of(payment));

        CurrentPaymentStateResponse response = service.getCurrentPayment(USER_ID);

        assertThat(response.status()).isEqualTo(PaymentTransactionStatus.RETRY_WAITING);
        assertThat(response.paymentType()).isEqualTo(PaymentTransactionType.REGULAR_PAYMENT);
        assertThat(response.amount()).isEqualTo(12_900L);
        assertThat(response.occurredAt()).isEqualTo(OffsetDateTime.parse("2026-09-07T09:00:00+09:00"));
    }

    @Test
    void 현재_결제가_없으면_null을_반환한다() {
        when(paymentTransactionRepository.findFirstByUserIdAndTransactionTypeInOrderByOccurredAtDescIdDesc(
            org.mockito.ArgumentMatchers.eq(USER_ID),
            org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(Optional.empty());

        assertThat(service.getCurrentPayment(USER_ID)).isNull();
    }

    @Test
    void 최근_환불은_고객의_단일_구독에_연결된_최신_환불을_반환한다() {
        Subscription subscription = mock(Subscription.class);
        Refund refund = mock(Refund.class);
        when(subscription.getId()).thenReturn(SUBSCRIPTION_ID);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(refundRepository.findFirstBySubscriptionIdOrderByRequestedAtDescIdDesc(SUBSCRIPTION_ID))
            .thenReturn(Optional.of(refund));
        when(refund.getStatus()).thenReturn(RefundStatus.REVIEW_REQUIRED);
        when(refund.getRefundType()).thenReturn(RefundType.DELIVERY_PARTIAL_CANCELLATION);
        when(refund.getRefundAmount()).thenReturn(20_000L);
        when(refund.getSuccessfulRefundAmount()).thenReturn(12_000L);
        when(refund.getUnprocessedAmount()).thenReturn(8_000L);
        when(refund.getRequestedAt()).thenReturn(OCCURRED_AT);
        when(refund.getCompletedAt()).thenReturn(null);

        CurrentRefundStateResponse response = service.getCurrentRefund(USER_ID);

        assertThat(response.status()).isEqualTo(RefundStatus.REVIEW_REQUIRED);
        assertThat(response.refundType()).isEqualTo(RefundType.DELIVERY_PARTIAL_CANCELLATION);
        assertThat(response.requestedAmount()).isEqualTo(20_000L);
        assertThat(response.refundedAmount()).isEqualTo(12_000L);
        assertThat(response.unprocessedAmount()).isEqualTo(8_000L);
        assertThat(response.requestedAt()).isEqualTo(OffsetDateTime.parse("2026-09-07T09:00:00+09:00"));
        assertThat(response.completedAt()).isNull();
    }

    @Test
    void 구독이나_환불이_없으면_최근_환불은_null이다() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        assertThat(service.getCurrentRefund(USER_ID)).isNull();
        verifyNoInteractions(refundRepository);

        Subscription subscription = mock(Subscription.class);
        when(subscription.getId()).thenReturn(SUBSCRIPTION_ID);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(refundRepository.findFirstBySubscriptionIdOrderByRequestedAtDescIdDesc(SUBSCRIPTION_ID))
            .thenReturn(Optional.empty());

        assertThat(service.getCurrentRefund(USER_ID)).isNull();
    }

    @ParameterizedTest
    @EnumSource(SubscriptionStatus.class)
    void 현재_구독은_모든_구독_상태를_그대로_반환한다(SubscriptionStatus status) {
        Subscription subscription = mock(Subscription.class);
        when(subscription.getStatus()).thenReturn(status);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));

        CurrentSubscriptionStateResponse response = service.getCurrentSubscription(USER_ID);

        assertThat(response.status()).isEqualTo(status);
    }

    @Test
    void 구독이_없으면_현재_구독은_null이다() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.getCurrentSubscription(USER_ID)).isNull();
    }

    @Test
    void 잘못된_고객_식별자는_Repository_조회_전에_거절한다() {
        assertThatThrownBy(() -> service.getCurrentPayment(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getCurrentRefund(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getCurrentSubscription(-1L)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(paymentTransactionRepository, refundRepository, subscriptionRepository);
    }

    private boolean containsOnlyOriginalPaymentTypes(Collection<PaymentTransactionType> types) {
        return types.size() == 3
            && types.contains(PaymentTransactionType.FIRST_SUBSCRIPTION_PAYMENT)
            && types.contains(PaymentTransactionType.REGULAR_PAYMENT)
            && types.contains(PaymentTransactionType.SETTING_CHANGE_PAYMENT);
    }
}
