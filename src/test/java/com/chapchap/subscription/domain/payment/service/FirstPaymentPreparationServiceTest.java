package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.command.FirstPaymentPrepareCommand;
import com.chapchap.subscription.domain.payment.service.result.PreparedFirstPayment;
import com.chapchap.subscription.domain.payment.support.PaymentBusinessKeyGenerator;
import com.chapchap.subscription.global.validation.PublicIdFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirstPaymentPreparationServiceTest {
    private static final Long PERIOD_ID = 30L;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @InjectMocks
    private FirstPaymentPreparationService service;

    @Test
    void 새_첫_결제_거래를_PROCESSING으로_준비한다() {
        FirstPaymentPrepareCommand command = command();
        String businessKey = PaymentBusinessKeyGenerator.firstPayment(PERIOD_ID);
        when(paymentTransactionRepository.findByBusinessDeduplicationKey(businessKey))
            .thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
            .thenAnswer(invocation -> {
                PaymentTransaction transaction = invocation.getArgument(0);
                ReflectionTestUtils.setField(transaction, "id", 100L);
                return transaction;
            });

        PreparedFirstPayment result = service.prepare(command);

        assertThat(result.paymentTransactionId()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo(PaymentTransactionStatus.PROCESSING);
        assertThat(result.newlyCreated()).isTrue();
        assertThat(PublicIdFormat.isUuidV4(result.paymentPublicId())).isTrue();
        verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    void 같은_업무_키의_거래가_있으면_새로_생성하지_않는다() {
        PaymentTransaction existing = transaction();
        ReflectionTestUtils.setField(existing, "id", 101L);
        when(paymentTransactionRepository.findByBusinessDeduplicationKey(
            PaymentBusinessKeyGenerator.firstPayment(PERIOD_ID)
        )).thenReturn(Optional.of(existing));

        PreparedFirstPayment result = service.prepare(command());

        assertThat(result.paymentTransactionId()).isEqualTo(101L);
        assertThat(result.newlyCreated()).isFalse();
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void 기존_거래가_완료됐어도_같은_기간의_거래를_다시_만들지_않는다() {
        PaymentTransaction existing = transaction();
        ReflectionTestUtils.setField(existing, "id", 102L);
        existing.markAsSucceeded();
        when(paymentTransactionRepository.findByBusinessDeduplicationKey(
            PaymentBusinessKeyGenerator.firstPayment(PERIOD_ID)
        )).thenReturn(Optional.of(existing));

        PreparedFirstPayment result = service.prepare(command());

        assertThat(result.status()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(result.newlyCreated()).isFalse();
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void 식별자가_양수가_아니면_준비_입력을_거절한다() {
        assertThatThrownBy(() -> new FirstPaymentPrepareCommand(
            1L,
            20L,
            0L,
            100_000L,
            LocalDateTime.of(2026, 9, 3, 15, 0),
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 10, 4)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 결제금액이_양수가_아니면_준비_입력을_거절한다() {
        assertThatThrownBy(() -> new FirstPaymentPrepareCommand(
            1L,
            20L,
            PERIOD_ID,
            0L,
            LocalDateTime.of(2026, 9, 3, 15, 0),
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 10, 4)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 종료일이_시작일보다_빠르면_준비_입력을_거절한다() {
        assertThatThrownBy(() -> new FirstPaymentPrepareCommand(
            1L,
            20L,
            PERIOD_ID,
            100_000L,
            LocalDateTime.of(2026, 9, 3, 15, 0),
            LocalDate.of(2026, 10, 4),
            LocalDate.of(2026, 9, 7)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private FirstPaymentPrepareCommand command() {
        return new FirstPaymentPrepareCommand(
            1L,
            20L,
            PERIOD_ID,
            100_000L,
            LocalDateTime.of(2026, 9, 3, 15, 0),
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 10, 4)
        );
    }

    private PaymentTransaction transaction() {
        FirstPaymentPrepareCommand command = command();
        return PaymentTransaction.createFirstSubscriptionPayment(
            command.userId(),
            command.subscriptionId(),
            command.subscriptionPeriodId(),
            command.transactionAmount(),
            command.processingReferenceAt(),
            command.periodStartDate(),
            command.periodEndDate(),
            "request-key-1",
            command.processingReferenceAt()
        );
    }
}
