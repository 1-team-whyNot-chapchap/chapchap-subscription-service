package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.client.AutomaticPaymentResult;
import com.chapchap.subscription.domain.payment.client.AutomaticPaymentStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentAttemptResult;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.command.PaymentAllocationCommand;
import com.chapchap.subscription.domain.payment.service.result.CompletedFirstPayment;
import com.chapchap.subscription.domain.payment.service.result.FirstPaymentExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirstPaymentCompletionServiceTest {
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;
    @Mock
    private PaymentAllocationRepository paymentAllocationRepository;

    @InjectMocks
    private FirstPaymentCompletionService service;

    @Test
    void 성공_응답은_시도와_주문별_배분을_기록하고_거래를_성공으로_확정한다() {
        PaymentTransaction transaction = processingTransaction();
        when(paymentTransactionRepository.findById(100L)).thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.existsByIdempotencyKey("request-key-1")).thenReturn(false);

        CompletedFirstPayment result = service.complete(
            successResult(),
            List.of(
                new PaymentAllocationCommand(501L, 40_000L),
                new PaymentAllocationCommand(502L, 60_000L)
            )
        );

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(transaction.getOriginalPaymentAmount()).isEqualTo(100_000L);
        assertThat(transaction.getCancelableAmount()).isEqualTo(100_000L);
        assertThat(transaction.getExternalRequestIdempotencyKey()).isNull();
        assertThat(result.attemptResult()).isEqualTo(PaymentAttemptResult.SUCCESS);
        assertThat(result.allocationCount()).isEqualTo(2);
        assertThat(result.providerStatus()).isEqualTo(AutomaticPaymentStatus.PAID);

        ArgumentCaptor<PaymentAttempt> attemptCaptor = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(paymentAttemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getPaymentMethodId()).isEqualTo(200L);
        assertThat(attemptCaptor.getValue().getIdempotencyKey()).isEqualTo("request-key-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PaymentAllocation>> allocationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paymentAllocationRepository).saveAll(allocationsCaptor.capture());
        assertThat(allocationsCaptor.getValue())
            .extracting(PaymentAllocation::getAllocatedAmount)
            .containsExactly(40_000L, 60_000L);
    }

    @Test
    void 명시적_거절은_실패_시도만_기록하고_거래를_실패로_확정한다() {
        PaymentTransaction transaction = processingTransaction();
        when(paymentTransactionRepository.findById(100L)).thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.existsByIdempotencyKey("request-key-1")).thenReturn(false);

        CompletedFirstPayment result = service.complete(failureResult(), List.of());

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(result.attemptResult()).isEqualTo(PaymentAttemptResult.FAILURE);
        assertThat(result.allocationCount()).isZero();
        assertThat(result.providerStatus()).isEqualTo(AutomaticPaymentStatus.DECLINED);
        verify(paymentAttemptRepository).save(any(PaymentAttempt.class));
        verify(paymentAllocationRepository, never()).saveAll(any());
    }

    @Test
    void Provider_설정_오류는_실패를_저장한_뒤_PAYMENT_002_분류를_반환한다() {
        PaymentTransaction transaction = processingTransaction();
        when(paymentTransactionRepository.findById(100L)).thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.existsByIdempotencyKey("request-key-1")).thenReturn(false);
        FirstPaymentExecutionResult executionResult = executionResult(
            AutomaticPaymentResult.providerConfigurationFailed(
                "550e8400-e29b-41d4-a716-446655440000",
                "CHANNELNOTFOUND",
                "외부 결제 연동 설정 오류가 발생했습니다."
            )
        );

        CompletedFirstPayment result = service.complete(executionResult, List.of());

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(transaction.getExternalRequestIdempotencyKey()).isNull();
        assertThat(result.providerStatus())
            .isEqualTo(AutomaticPaymentStatus.PROVIDER_CONFIGURATION_FAILED);
        verify(paymentAttemptRepository).save(any(PaymentAttempt.class));
        verify(paymentAllocationRepository, never()).saveAll(any());
    }

    @Test
    void 성공_배분_합계가_거래금액과_다르면_아무것도_확정하지_않는다() {
        PaymentTransaction transaction = processingTransaction();
        when(paymentTransactionRepository.findById(100L)).thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.existsByIdempotencyKey("request-key-1")).thenReturn(false);

        assertThatThrownBy(() -> service.complete(
            successResult(),
            List.of(new PaymentAllocationCommand(501L, 90_000L))
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.PROCESSING);
        verify(paymentAttemptRepository, never()).save(any());
        verify(paymentAllocationRepository, never()).saveAll(any());
    }

    @Test
    void 같은_주문을_두_번_배분하면_거절한다() {
        PaymentTransaction transaction = processingTransaction();
        when(paymentTransactionRepository.findById(100L)).thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.existsByIdempotencyKey("request-key-1")).thenReturn(false);

        assertThatThrownBy(() -> service.complete(
            successResult(),
            List.of(
                new PaymentAllocationCommand(501L, 40_000L),
                new PaymentAllocationCommand(501L, 60_000L)
            )
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.PROCESSING);
        verify(paymentAttemptRepository, never()).save(any());
        verify(paymentAllocationRepository, never()).saveAll(any());
    }

    @Test
    void 실패_응답에_배분을_전달하면_거절한다() {
        PaymentTransaction transaction = processingTransaction();
        when(paymentTransactionRepository.findById(100L)).thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.existsByIdempotencyKey("request-key-1")).thenReturn(false);

        assertThatThrownBy(() -> service.complete(
            failureResult(),
            List.of(new PaymentAllocationCommand(501L, 100_000L))
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.PROCESSING);
        verify(paymentAttemptRepository, never()).save(any());
    }

    @Test
    void 같은_멱등성_키의_응답이_이미_기록됐으면_중복_확정하지_않는다() {
        PaymentTransaction transaction = processingTransaction();
        when(paymentTransactionRepository.findById(100L)).thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.existsByIdempotencyKey("request-key-1")).thenReturn(true);

        assertThatThrownBy(() -> service.complete(successResult(), List.of(
            new PaymentAllocationCommand(501L, 100_000L)
        ))).isInstanceOf(IllegalStateException.class);

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.PROCESSING);
        verify(paymentAttemptRepository, never()).save(any());
        verify(paymentAllocationRepository, never()).saveAll(any());
    }

    @Test
    void 이미_완료된_거래는_다시_확정하지_않는다() {
        PaymentTransaction transaction = processingTransaction();
        transaction.markAsSucceeded();
        when(paymentTransactionRepository.findById(100L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.complete(successResult(), List.of(
            new PaymentAllocationCommand(501L, 100_000L)
        ))).isInstanceOf(IllegalStateException.class);

        verify(paymentAttemptRepository, never()).existsByIdempotencyKey(any());
        verify(paymentAttemptRepository, never()).save(any());
    }

    @Test
    void 외부_결제_ID가_거래_공개_ID와_다르면_결과를_확정하지_않는다() {
        PaymentTransaction transaction = processingTransaction();
        when(paymentTransactionRepository.findById(100L)).thenReturn(Optional.of(transaction));

        FirstPaymentExecutionResult mismatchedResult = executionResult(AutomaticPaymentResult.success(
            "650e8400-e29b-41d4-a716-446655440000",
            "transaction-ref-1",
            "PAID"
        ));

        assertThatThrownBy(() -> service.complete(
            mismatchedResult,
            List.of(new PaymentAllocationCommand(501L, 100_000L))
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("External payment id");

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.PROCESSING);
        verify(paymentAttemptRepository, never()).existsByIdempotencyKey(any());
        verify(paymentAttemptRepository, never()).save(any());
        verify(paymentAllocationRepository, never()).saveAll(any());
    }

    private FirstPaymentExecutionResult successResult() {
        return executionResult(AutomaticPaymentResult.success(
            "550e8400-e29b-41d4-a716-446655440000",
            "transaction-ref-1",
            "PAID"
        ));
    }

    private FirstPaymentExecutionResult failureResult() {
        return executionResult(AutomaticPaymentResult.declined(
            "550e8400-e29b-41d4-a716-446655440000",
            "DECLINED",
            "카드 승인이 거절되었습니다."
        ));
    }

    private FirstPaymentExecutionResult executionResult(AutomaticPaymentResult providerResult) {
        return new FirstPaymentExecutionResult(
            100L,
            200L,
            PaymentProviderCode.PORTONE,
            "request-key-1",
            100_000L,
            LocalDateTime.of(2026, 9, 3, 15, 1),
            LocalDateTime.of(2026, 9, 3, 15, 1, 1),
            providerResult
        );
    }

    private PaymentTransaction processingTransaction() {
        PaymentTransaction transaction = PaymentTransaction.createFirstSubscriptionPayment(
            1L,
            20L,
            30L,
            100_000L,
            LocalDateTime.of(2026, 9, 3, 15, 0),
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 10, 4),
            "request-key-1",
            LocalDateTime.of(2026, 9, 3, 15, 0)
        );
        ReflectionTestUtils.setField(transaction, "id", 100L);
        ReflectionTestUtils.setField(transaction, "publicId", "550e8400-e29b-41d4-a716-446655440000");
        return transaction;
    }
}
