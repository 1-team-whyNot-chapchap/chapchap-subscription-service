package com.chapchap.subscription.global.kafka.customer;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPaymentEventPublisherTest {
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private PaymentTransactionRepository payments;
    @Mock private OrderRepository orders;
    @Mock private PaymentTransaction payment;
    @Mock private Order order;
    private CustomerPaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        CustomerPaymentKafkaProperties properties = new CustomerPaymentKafkaProperties();
        properties.setTopic("subscription.payment-events.v1");
        publisher = new CustomerPaymentEventPublisher(kafkaTemplate, properties, payments, orders);
        org.mockito.Mockito.lenient().when(payments.findById(1L)).thenReturn(Optional.of(payment));
        org.mockito.Mockito.lenient().when(payment.getPublicId()).thenReturn("11111111-1111-4111-8111-111111111111");
        org.mockito.Mockito.lenient().when(payment.getUserId()).thenReturn(25L);
        org.mockito.Mockito.lenient().when(payment.getPaymentStateVersion()).thenReturn(3L);
        org.mockito.Mockito.lenient().when(payment.getTransactionAmount()).thenReturn(12_900L);
        org.mockito.Mockito.lenient().when(payment.getPeriodStartDate()).thenReturn(LocalDate.of(2026, 9, 1));
        org.mockito.Mockito.lenient().when(payment.getPeriodEndDate()).thenReturn(LocalDate.of(2026, 9, 28));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 정기결제_성공은_주문_할인_합계와_확정된_계약으로_발행한다() {
        when(payment.getTransactionType()).thenReturn(PaymentTransactionType.REGULAR_PAYMENT);
        when(payment.getStatus()).thenReturn(PaymentTransactionStatus.SUCCESS);
        when(payment.getSubscriptionPeriodId()).thenReturn(10L);
        when(orders.findAllBySubscriptionPeriodId(10L)).thenReturn(List.of(order));
        when(order.getDiscountAmount()).thenReturn(1_000L);

        publisher.publishCompletedAfterCommit(1L, LocalDateTime.of(2026, 9, 1, 9, 0));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.eq("subscription.payment-events.v1"),
            org.mockito.ArgumentMatchers.eq("11111111-1111-4111-8111-111111111111"),
            eventCaptor.capture()
        );
        PaymentCompletedEvent event = (PaymentCompletedEvent) eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.occurredAt().getOffset().getTotalSeconds()).isEqualTo(9 * 60 * 60);
        assertThat(event.data().paymentStatus()).isEqualTo("COMPLETED");
        assertThat(event.data().discountAmount()).isEqualTo(1_000L);
        assertThat(event.data().currency()).isEqualTo("KRW");
    }

    @Test
    void 오전_실패는_13시_재시도_예정값을_포함하고_DB_커밋_전에는_발행하지_않는다() {
        when(payment.getTransactionType()).thenReturn(PaymentTransactionType.REGULAR_PAYMENT);
        when(payment.getStatus()).thenReturn(PaymentTransactionStatus.RETRY_WAITING);
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishRegularFailureAfterCommit(1L, LocalDateTime.of(2026, 9, 1, 9, 0, 3), false);

        verify(kafkaTemplate, never()).send(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.eq("subscription.payment-events.v1"),
            org.mockito.ArgumentMatchers.eq("11111111-1111-4111-8111-111111111111"),
            eventCaptor.capture()
        );
        PaymentFailedEvent event = (PaymentFailedEvent) eventCaptor.getValue();
        PaymentFailedEvent.RetryWaitingData data = (PaymentFailedEvent.RetryWaitingData) event.data();
        assertThat(data.paymentStatus()).isEqualTo("RETRY_WAITING");
        assertThat(data.retryScheduledAt().toLocalTime()).isEqualTo(java.time.LocalTime.of(13, 0));
    }

    @Test
    void 최종_실패는_현재_기간_종료일과_공개_실패_코드를_사용한다() {
        when(payment.getTransactionType()).thenReturn(PaymentTransactionType.REGULAR_PAYMENT);
        when(payment.getStatus()).thenReturn(PaymentTransactionStatus.FAILED);

        publisher.publishRegularFailureAfterCommit(1L, LocalDateTime.of(2026, 9, 1, 13, 0), true);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), eventCaptor.capture()
        );
        PaymentFailedEvent.FinalFailureData data =
            (PaymentFailedEvent.FinalFailureData) ((PaymentFailedEvent) eventCaptor.getValue()).data();
        assertThat(data.currentPeriodEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(data.failureCode()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void 설정변경_추가결제_성공은_할인금액을_항상_0으로_발행한다() {
        when(payment.getTransactionType()).thenReturn(PaymentTransactionType.SETTING_CHANGE_PAYMENT);
        when(payment.getStatus()).thenReturn(PaymentTransactionStatus.SUCCESS);

        publisher.publishCompletedAfterCommit(1L, LocalDateTime.of(2026, 9, 1, 14, 0));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), eventCaptor.capture()
        );
        assertThat(((PaymentCompletedEvent) eventCaptor.getValue()).data().discountAmount()).isZero();
        verify(orders, never()).findAllBySubscriptionPeriodId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 설정변경_추가결제_실패는_적용일과_공개_실패코드를_발행한다() {
        when(payment.getTransactionType()).thenReturn(PaymentTransactionType.SETTING_CHANGE_PAYMENT);
        when(payment.getStatus()).thenReturn(PaymentTransactionStatus.FAILED);
        when(payment.getSettingEffectiveDate()).thenReturn(LocalDate.of(2026, 9, 7));

        publisher.publishSettingChangeFailureAfterCommit(1L, LocalDateTime.of(2026, 9, 6, 14, 0));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), eventCaptor.capture()
        );
        PaymentFailedEvent.SettingChangeFailureData data =
            (PaymentFailedEvent.SettingChangeFailureData) ((PaymentFailedEvent) eventCaptor.getValue()).data();
        assertThat(data.settingEffectiveDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(data.failureCode()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void 재시도_중단은_결제상태와_같은_업무순번으로_발행한다() {
        when(payment.getTransactionType()).thenReturn(PaymentTransactionType.REGULAR_PAYMENT);
        when(payment.getStatus()).thenReturn(PaymentTransactionStatus.RETRY_STOPPED);

        publisher.publishRetryStoppedAfterCommit(payment, LocalDateTime.of(2026, 9, 6, 11, 0));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), eventCaptor.capture()
        );
        PaymentRetryStoppedEvent event = (PaymentRetryStoppedEvent) eventCaptor.getValue();
        assertThat(event.data().paymentStatus()).isEqualTo("RETRY_STOPPED");
        assertThat(event.data().paymentVersion()).isEqualTo(3L);
    }

    @Test
    void 같은_결제_업무사실의_재발행은_동일한_eventId를_사용한다() {
        when(payment.getTransactionType()).thenReturn(PaymentTransactionType.SETTING_CHANGE_PAYMENT);
        when(payment.getStatus()).thenReturn(PaymentTransactionStatus.SUCCESS);

        publisher.publishCompletedAfterCommit(1L, LocalDateTime.of(2026, 9, 6, 14, 0));
        publisher.publishCompletedAfterCommit(1L, LocalDateTime.of(2026, 9, 6, 14, 0));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), eventCaptor.capture()
        );
        var eventIds = eventCaptor.getAllValues().stream()
            .map(value -> ((PaymentCompletedEvent) value).eventId())
            .toList();
        assertThat(eventIds).hasSize(2).allMatch(eventIds.getFirst()::equals);
    }
}
