package com.chapchap.subscription.global.kafka.customer;

import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRefundEventPublisherTest {
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private Refund refund;
    @Mock private PaymentTransaction cancellation;
    private CustomerRefundEventPublisher publisher;

    @BeforeEach
    void setUp() {
        CustomerRefundKafkaProperties properties = new CustomerRefundKafkaProperties();
        properties.setTopic("subscription.refund-events.v1");
        publisher = new CustomerRefundEventPublisher(kafkaTemplate, properties);
        org.mockito.Mockito.lenient().when(refund.getPublicId()).thenReturn("22222222-2222-4222-8222-222222222222");
        org.mockito.Mockito.lenient().when(refund.getRefundType()).thenReturn(RefundType.NEXT_PERIOD_FULL_CANCELLATION);
        org.mockito.Mockito.lenient().when(cancellation.getUserId()).thenReturn(25L);
    }

    @Test
    void 환불_완료는_환불_업무_단위로_발행한다() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 9, 1, 15, 0);
        when(refund.getStatus()).thenReturn(RefundStatus.COMPLETED);
        when(refund.getSuccessfulRefundAmount()).thenReturn(13_900L);
        when(refund.getCompletedAt()).thenReturn(completedAt);

        publisher.publishTerminalAfterCommit(refund, cancellation, completedAt);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.eq("subscription.refund-events.v1"),
            org.mockito.ArgumentMatchers.eq("22222222-2222-4222-8222-222222222222"),
            eventCaptor.capture()
        );
        RefundCompletedEvent event = (RefundCompletedEvent) eventCaptor.getValue();
        assertThat(event.data().refundType()).isEqualTo("NEXT_PERIOD_FULL_CANCELLATION");
        assertThat(event.data().refundedAmount()).isEqualTo(13_900L);
        assertThat(event.data().currency()).isEqualTo("KRW");
    }

    @Test
    void 첫_취소_실패는_PG_원문이_아닌_공개_실패_코드로_발행한다() {
        when(refund.getStatus()).thenReturn(RefundStatus.FAILED);
        when(refund.getRefundAmount()).thenReturn(13_900L);
        when(cancellation.getPublicId()).thenReturn("33333333-3333-4333-8333-333333333333");

        publisher.publishTerminalAfterCommit(refund, cancellation, LocalDateTime.of(2026, 9, 1, 15, 10));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), eventCaptor.capture()
        );
        RefundFailedEvent event = (RefundFailedEvent) eventCaptor.getValue();
        assertThat(event.data().failureCode()).isEqualTo("REFUND_FAILED");
        assertThat(event.data().requestedAmount()).isEqualTo(13_900L);
    }

    @Test
    void 일부_취소_후_확인필요_환불은_발행하지_않는다() {
        when(refund.getStatus()).thenReturn(RefundStatus.REVIEW_REQUIRED);

        publisher.publishTerminalAfterCommit(refund, cancellation, LocalDateTime.of(2026, 9, 1, 15, 10));

        verify(kafkaTemplate, never()).send(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
    }
}
