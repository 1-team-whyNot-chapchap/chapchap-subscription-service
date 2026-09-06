package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentAttemptResult;
import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import com.chapchap.subscription.domain.payment.entity.PaymentMethodStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;
import com.chapchap.subscription.domain.payment.entity.RefundType;
import com.chapchap.subscription.domain.payment.repository.PaymentAttemptRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentMethodRepository;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.payment.response.PaymentDetailResponse;
import com.chapchap.subscription.domain.payment.response.PaymentListResponse;
import com.chapchap.subscription.domain.payment.response.RefundDetailResponse;
import com.chapchap.subscription.domain.payment.response.RefundListResponse;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import com.chapchap.subscription.global.exception.payment.PaymentHistoryNotFoundException;
import com.chapchap.subscription.global.exception.payment.RefundHistoryNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentHistoryQueryServiceTest {
    private static final Long USER_ID = 10L;
    private static final Long SUBSCRIPTION_ID = 30L;
    private static final String PAYMENT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String REFUND_ID = "650e8400-e29b-41d4-a716-446655440000";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 9, 5, 14, 0);

    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    private PaymentHistoryQueryService service;

    @BeforeEach
    void setUp() {
        service = new PaymentHistoryQueryService(
            paymentTransactionRepository,
            paymentAttemptRepository,
            paymentMethodRepository,
            refundRepository,
            subscriptionRepository
        );
    }

    @Test
    void 결제_목록이_없으면_빈_배열을_반환한다() {
        when(paymentTransactionRepository.findAllByUserIdOrderByOccurredAtDescIdDesc(USER_ID))
            .thenReturn(List.of());

        assertThat(service.getPayments(USER_ID).payments()).isEmpty();
    }

    @Test
    void 결제_목록은_Repository_정렬_순서와_저장값을_유지한다() {
        PaymentTransaction recent = payment(20L, "11111111-1111-4111-8111-111111111111", OCCURRED_AT.plusHours(1));
        PaymentTransaction older = payment(21L, "22222222-2222-4222-8222-222222222222", OCCURRED_AT);
        when(paymentTransactionRepository.findAllByUserIdOrderByOccurredAtDescIdDesc(USER_ID))
            .thenReturn(List.of(recent, older));

        PaymentListResponse response = service.getPayments(USER_ID);

        assertThat(response.payments())
            .extracting(PaymentListResponse.PaymentItemResponse::paymentId)
            .containsExactly(
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222"
            );
        assertThat(response.payments().getFirst().amount()).isEqualTo(89_000L);
    }

    @Test
    void 삭제된_결제수단도_과거_처리_시도의_카드_표시정보만_반환한다() {
        PaymentTransaction transaction = payment(20L, PAYMENT_ID, OCCURRED_AT);
        PaymentAttempt attempt = attempt(20L, 70L);
        PaymentMethod paymentMethod = paymentMethod(70L, USER_ID);
        when(paymentTransactionRepository.findByPublicIdAndUserId(PAYMENT_ID, USER_ID))
            .thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(20L))
            .thenReturn(List.of(attempt));
        when(paymentMethodRepository.findAllById(any())).thenReturn(List.of(paymentMethod));

        PaymentDetailResponse response = service.getPayment(USER_ID, PAYMENT_ID);

        assertThat(response.originalPaymentAmount()).isEqualTo(89_000L);
        assertThat(response.attempts()).hasSize(1);
        assertThat(response.attempts().getFirst().cardCompany()).isEqualTo("테스트카드");
        assertThat(response.attempts().getFirst().maskedCardNumber()).isEqualTo("****-1234");
        assertThat(recordFields(PaymentDetailResponse.class))
            .doesNotContain("externalPaymentId", "externalTransactionRef", "idempotencyKey", "failureReason");
        assertThat(recordFields(PaymentDetailResponse.PaymentAttemptResponse.class))
            .doesNotContain("externalPaymentId", "externalTransactionRef", "idempotencyKey", "failureReason");
    }

    @Test
    void 처리_중_결제에_시도_이력이_없어도_빈_배열을_반환한다() {
        PaymentTransaction transaction = payment(20L, PAYMENT_ID, OCCURRED_AT);
        when(transaction.getStatus()).thenReturn(PaymentTransactionStatus.PROCESSING);
        when(paymentTransactionRepository.findByPublicIdAndUserId(PAYMENT_ID, USER_ID))
            .thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(20L))
            .thenReturn(List.of());

        assertThat(service.getPayment(USER_ID, PAYMENT_ID).attempts()).isEmpty();
    }

    @Test
    void 원_결제_취소_시도는_카드_표시정보를_null로_반환한다() {
        PaymentTransaction transaction = payment(20L, PAYMENT_ID, OCCURRED_AT);
        when(transaction.getTransactionType()).thenReturn(PaymentTransactionType.CANCELLATION_BEFORE_START);
        PaymentAttempt attempt = attempt(20L, null);
        when(paymentTransactionRepository.findByPublicIdAndUserId(PAYMENT_ID, USER_ID))
            .thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(20L))
            .thenReturn(List.of(attempt));

        PaymentDetailResponse.PaymentAttemptResponse response =
            service.getPayment(USER_ID, PAYMENT_ID).attempts().getFirst();

        assertThat(response.cardCompany()).isNull();
        assertThat(response.maskedCardNumber()).isNull();
    }

    @Test
    void 처리_시도의_결제수단이_다른_고객_소유면_내부_정합성_오류로_차단한다() {
        PaymentTransaction transaction = payment(20L, PAYMENT_ID, OCCURRED_AT);
        PaymentAttempt attempt = attempt(20L, 70L);
        PaymentMethod paymentMethod = paymentMethod(70L, 999L);
        when(paymentTransactionRepository.findByPublicIdAndUserId(PAYMENT_ID, USER_ID))
            .thenReturn(Optional.of(transaction));
        when(paymentAttemptRepository.findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(20L))
            .thenReturn(List.of(attempt));
        when(paymentMethodRepository.findAllById(any())).thenReturn(List.of(paymentMethod));

        assertThatThrownBy(() -> service.getPayment(USER_ID, PAYMENT_ID))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 결제_상세가_없거나_다른_고객_소유면_같은_오류를_반환한다() {
        when(paymentTransactionRepository.findByPublicIdAndUserId(PAYMENT_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment(USER_ID, PAYMENT_ID))
            .isInstanceOf(PaymentHistoryNotFoundException.class);
        verifyNoInteractions(paymentAttemptRepository, paymentMethodRepository);
    }

    @Test
    void 잘못된_결제_공개_식별자는_DB를_조회하지_않는다() {
        assertThatThrownBy(() -> service.getPayment(USER_ID, "PAY-invalid"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(
            paymentTransactionRepository,
            paymentAttemptRepository,
            paymentMethodRepository,
            refundRepository,
            subscriptionRepository
        );
    }

    @Test
    void 구독이_없으면_환불_목록은_빈_배열이다() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.getRefunds(USER_ID).refunds()).isEmpty();
        verifyNoInteractions(refundRepository);
    }

    @Test
    void 환불_목록은_REVIEW_REQUIRED의_저장된_금액을_그대로_반환한다() {
        Subscription subscription = subscription();
        Refund refund = refund(40L, RefundStatus.REVIEW_REQUIRED);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(refundRepository.findAllBySubscriptionIdOrderByRequestedAtDescIdDesc(SUBSCRIPTION_ID))
            .thenReturn(List.of(refund));

        RefundListResponse.RefundItemResponse response = service.getRefunds(USER_ID).refunds().getFirst();

        assertThat(response.status()).isEqualTo(RefundStatus.REVIEW_REQUIRED);
        assertThat(response.requestedAmount()).isEqualTo(89_000L);
        assertThat(response.refundedAmount()).isEqualTo(50_000L);
        assertThat(response.unprocessedAmount()).isEqualTo(39_000L);
    }

    @Test
    void 환불_상세는_연결된_취소_거래와_원_결제_공개_식별자를_반환한다() {
        Subscription subscription = subscription();
        Refund refund = refund(40L, RefundStatus.REVIEW_REQUIRED);
        PaymentTransaction cancellation = cancellation(41L, 40L, 50L);
        PaymentTransaction original = originalPayment(50L);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(refundRepository.findByPublicIdAndSubscriptionId(REFUND_ID, SUBSCRIPTION_ID))
            .thenReturn(Optional.of(refund));
        when(paymentTransactionRepository.findAllByRefundIdOrderByOccurredAtAscIdAsc(40L))
            .thenReturn(List.of(cancellation));
        when(paymentTransactionRepository.findAllById(any())).thenReturn(List.of(original));

        RefundDetailResponse response = service.getRefund(USER_ID, REFUND_ID);

        assertThat(response.cancellations()).hasSize(1);
        assertThat(response.cancellations().getFirst().paymentId())
            .isEqualTo("31111111-1111-4111-8111-111111111111");
        assertThat(response.cancellations().getFirst().originalPaymentId())
            .isEqualTo("41111111-1111-4111-8111-111111111111");
        assertThat(response.cancellations().getFirst().amount()).isEqualTo(39_000L);
        assertThat(recordFields(RefundDetailResponse.class)).doesNotContain("failureReason", "businessDeduplicationKey");
        assertThat(recordFields(RefundDetailResponse.CancellationResponse.class))
            .doesNotContain("externalPaymentId", "externalTransactionRef", "failureReason");
    }

    @Test
    void 환불_상세에_취소_거래가_아직_없으면_빈_배열을_반환한다() {
        Subscription subscription = subscription();
        Refund refund = refund(40L, RefundStatus.PENDING);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(refundRepository.findByPublicIdAndSubscriptionId(REFUND_ID, SUBSCRIPTION_ID))
            .thenReturn(Optional.of(refund));
        when(paymentTransactionRepository.findAllByRefundIdOrderByOccurredAtAscIdAsc(40L))
            .thenReturn(List.of());

        assertThat(service.getRefund(USER_ID, REFUND_ID).cancellations()).isEmpty();
    }

    @Test
    void 환불이_없거나_다른_고객_구독에_속하면_같은_오류를_반환한다() {
        Subscription subscription = subscription();
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(refundRepository.findByPublicIdAndSubscriptionId(REFUND_ID, SUBSCRIPTION_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRefund(USER_ID, REFUND_ID))
            .isInstanceOf(RefundHistoryNotFoundException.class);
    }

    @Test
    void 잘못된_환불_공개_식별자는_DB를_조회하지_않는다() {
        assertThatThrownBy(() -> service.getRefund(USER_ID, "REF-invalid"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(
            paymentTransactionRepository,
            paymentAttemptRepository,
            paymentMethodRepository,
            refundRepository,
            subscriptionRepository
        );
    }

    @Test
    void 환불_취소_거래의_원_결제_참조가_깨졌으면_내부_정합성_오류로_차단한다() {
        Subscription subscription = subscription();
        Refund refund = refund(40L, RefundStatus.REVIEW_REQUIRED);
        PaymentTransaction cancellation = cancellation(41L, 40L, 50L);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(refundRepository.findByPublicIdAndSubscriptionId(REFUND_ID, SUBSCRIPTION_ID))
            .thenReturn(Optional.of(refund));
        when(paymentTransactionRepository.findAllByRefundIdOrderByOccurredAtAscIdAsc(40L))
            .thenReturn(List.of(cancellation));
        when(paymentTransactionRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.getRefund(USER_ID, REFUND_ID))
            .isInstanceOf(IllegalStateException.class);
    }

    private PaymentTransaction payment(Long id, String publicId, LocalDateTime occurredAt) {
        PaymentTransaction payment = mock(PaymentTransaction.class);
        lenient().when(payment.getId()).thenReturn(id);
        lenient().when(payment.getPublicId()).thenReturn(publicId);
        lenient().when(payment.getUserId()).thenReturn(USER_ID);
        lenient().when(payment.getSubscriptionId()).thenReturn(SUBSCRIPTION_ID);
        lenient().when(payment.getSubscriptionPeriodId()).thenReturn(60L);
        lenient().when(payment.getTransactionType()).thenReturn(PaymentTransactionType.FIRST_SUBSCRIPTION_PAYMENT);
        lenient().when(payment.getStatus()).thenReturn(PaymentTransactionStatus.SUCCESS);
        lenient().when(payment.getTransactionAmount()).thenReturn(89_000L);
        lenient().when(payment.getOriginalPaymentAmount()).thenReturn(89_000L);
        lenient().when(payment.getCumulativeCancelAmount()).thenReturn(0L);
        lenient().when(payment.getCancelableAmount()).thenReturn(89_000L);
        lenient().when(payment.getPeriodStartDate()).thenReturn(LocalDate.of(2026, 9, 7));
        lenient().when(payment.getPeriodEndDate()).thenReturn(LocalDate.of(2026, 10, 4));
        lenient().when(payment.getOccurredAt()).thenReturn(occurredAt);
        return payment;
    }

    private PaymentAttempt attempt(Long transactionId, Long paymentMethodId) {
        PaymentAttempt attempt = mock(PaymentAttempt.class);
        lenient().when(attempt.getPaymentTransactionId()).thenReturn(transactionId);
        lenient().when(attempt.getPaymentMethodId()).thenReturn(paymentMethodId);
        lenient().when(attempt.getAttemptSequence()).thenReturn(1);
        lenient().when(attempt.getRequestedAmount()).thenReturn(89_000L);
        lenient().when(attempt.getRequestedAt()).thenReturn(OCCURRED_AT);
        lenient().when(attempt.getRespondedAt()).thenReturn(OCCURRED_AT.plusSeconds(1));
        lenient().when(attempt.getResult()).thenReturn(PaymentAttemptResult.SUCCESS);
        return attempt;
    }

    private PaymentMethod paymentMethod(Long id, Long userId) {
        PaymentMethod paymentMethod = mock(PaymentMethod.class);
        lenient().when(paymentMethod.getId()).thenReturn(id);
        lenient().when(paymentMethod.getUserId()).thenReturn(userId);
        lenient().when(paymentMethod.getStatus()).thenReturn(PaymentMethodStatus.DELETED);
        lenient().when(paymentMethod.getCardCompany()).thenReturn("테스트카드");
        lenient().when(paymentMethod.getMaskedCardNumber()).thenReturn("****-1234");
        return paymentMethod;
    }

    private Subscription subscription() {
        Subscription subscription = mock(Subscription.class);
        lenient().when(subscription.getId()).thenReturn(SUBSCRIPTION_ID);
        lenient().when(subscription.getUserId()).thenReturn(USER_ID);
        return subscription;
    }

    private Refund refund(Long id, RefundStatus status) {
        Refund refund = mock(Refund.class);
        lenient().when(refund.getId()).thenReturn(id);
        lenient().when(refund.getPublicId()).thenReturn(REFUND_ID);
        lenient().when(refund.getSubscriptionId()).thenReturn(SUBSCRIPTION_ID);
        lenient().when(refund.getRefundType()).thenReturn(RefundType.CANCELLATION_BEFORE_START);
        lenient().when(refund.getStatus()).thenReturn(status);
        lenient().when(refund.getRefundAmount()).thenReturn(89_000L);
        lenient().when(refund.getSuccessfulRefundAmount()).thenReturn(50_000L);
        lenient().when(refund.getUnprocessedAmount()).thenReturn(39_000L);
        lenient().when(refund.getRequestedAt()).thenReturn(OCCURRED_AT);
        return refund;
    }

    private PaymentTransaction cancellation(Long id, Long refundId, Long originalId) {
        PaymentTransaction cancellation = payment(
            id, "31111111-1111-4111-8111-111111111111", OCCURRED_AT.plusSeconds(2)
        );
        lenient().when(cancellation.getTransactionType())
            .thenReturn(PaymentTransactionType.CANCELLATION_BEFORE_START);
        lenient().when(cancellation.getRefundId()).thenReturn(refundId);
        lenient().when(cancellation.getOriginalPaymentTransactionId()).thenReturn(originalId);
        lenient().when(cancellation.getTransactionAmount()).thenReturn(39_000L);
        return cancellation;
    }

    private PaymentTransaction originalPayment(Long id) {
        PaymentTransaction original = payment(id, "41111111-1111-4111-8111-111111111111", OCCURRED_AT.minusDays(1));
        lenient().when(original.getStatus()).thenReturn(PaymentTransactionStatus.SUCCESS);
        return original;
    }

    private List<String> recordFields(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(component -> component.getName()).toList();
    }
}
