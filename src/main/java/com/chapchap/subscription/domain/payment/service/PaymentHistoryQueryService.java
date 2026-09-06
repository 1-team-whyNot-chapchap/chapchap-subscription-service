package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.Refund;
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
import com.chapchap.subscription.global.validation.PublicIdFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 인증 고객의 저장된 결제·환불 이력만 읽고 외부 처리나 상태 변경을 시작하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentHistoryQueryService {
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final RefundRepository refundRepository;
    private final SubscriptionRepository subscriptionRepository;

    public PaymentListResponse getPayments(Long userId) {
        validateUserId(userId);
        return new PaymentListResponse(
            paymentTransactionRepository.findAllByUserIdOrderByOccurredAtDescIdDesc(userId)
                .stream()
                .map(this::toPaymentListItem)
                .toList()
        );
    }

    public PaymentDetailResponse getPayment(Long userId, String paymentId) {
        validateUserId(userId);
        validatePaymentId(paymentId);
        PaymentTransaction transaction = paymentTransactionRepository.findByPublicIdAndUserId(paymentId, userId)
            .orElseThrow(PaymentHistoryNotFoundException::new);
        Long transactionId = requirePositive(transaction.getId(), "결제 거래");
        List<PaymentAttempt> attempts = paymentAttemptRepository
            .findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(transactionId);
        Map<Long, PaymentMethod> paymentMethods = loadPaymentMethods(attempts);

        return new PaymentDetailResponse(
            transaction.getPublicId(),
            transaction.getTransactionType(),
            transaction.getStatus(),
            transaction.getTransactionAmount(),
            transaction.getOccurredAt(),
            transaction.getOriginalPaymentAmount(),
            transaction.getCumulativeCancelAmount(),
            transaction.getCancelableAmount(),
            transaction.getPeriodStartDate(),
            transaction.getPeriodEndDate(),
            attempts.stream()
                .map(attempt -> toPaymentAttempt(
                    userId,
                    transactionId,
                    transaction.getTransactionType(),
                    attempt,
                    paymentMethods
                ))
                .toList()
        );
    }

    public RefundListResponse getRefunds(Long userId) {
        validateUserId(userId);
        return subscriptionRepository.findByUserId(userId)
            .map(subscription -> {
                Long subscriptionId = requirePositive(subscription.getId(), "구독");
                return new RefundListResponse(
                    refundRepository.findAllBySubscriptionIdOrderByRequestedAtDescIdDesc(subscriptionId)
                        .stream()
                        .map(refund -> toRefundListItem(subscriptionId, refund))
                        .toList()
                );
            })
            .orElseGet(() -> new RefundListResponse(List.of()));
    }

    public RefundDetailResponse getRefund(Long userId, String refundId) {
        validateUserId(userId);
        validateRefundId(refundId);
        Subscription subscription = subscriptionRepository.findByUserId(userId)
            .orElseThrow(RefundHistoryNotFoundException::new);
        Long subscriptionId = requirePositive(subscription.getId(), "구독");
        Refund refund = refundRepository.findByPublicIdAndSubscriptionId(refundId, subscriptionId)
            .orElseThrow(RefundHistoryNotFoundException::new);
        Long internalRefundId = requirePositive(refund.getId(), "환불");
        List<PaymentTransaction> cancellations = paymentTransactionRepository
            .findAllByRefundIdOrderByOccurredAtAscIdAsc(internalRefundId);
        Map<Long, PaymentTransaction> originals = loadOriginalPayments(cancellations);

        validateRefund(subscriptionId, refund);
        return new RefundDetailResponse(
            refund.getPublicId(),
            refund.getRefundType(),
            refund.getStatus(),
            refund.getRefundAmount(),
            refund.getSuccessfulRefundAmount(),
            refund.getUnprocessedAmount(),
            refund.getRequestedAt(),
            refund.getCompletedAt(),
            cancellations.stream()
                .map(cancellation -> toCancellation(userId, subscriptionId, internalRefundId, cancellation, originals))
                .toList()
        );
    }

    private PaymentListResponse.PaymentItemResponse toPaymentListItem(PaymentTransaction transaction) {
        return new PaymentListResponse.PaymentItemResponse(
            transaction.getPublicId(),
            transaction.getTransactionType(),
            transaction.getStatus(),
            transaction.getTransactionAmount(),
            transaction.getOccurredAt()
        );
    }

    private PaymentDetailResponse.PaymentAttemptResponse toPaymentAttempt(
        Long userId,
        Long transactionId,
        PaymentTransactionType transactionType,
        PaymentAttempt attempt,
        Map<Long, PaymentMethod> paymentMethods
    ) {
        if (!transactionId.equals(attempt.getPaymentTransactionId())) {
            throw inconsistentData();
        }
        boolean cancellation = isCancellation(transactionType);
        if (cancellation != (attempt.getPaymentMethodId() == null)) {
            throw inconsistentData();
        }
        PaymentMethod paymentMethod = null;
        if (attempt.getPaymentMethodId() != null) {
            paymentMethod = paymentMethods.get(attempt.getPaymentMethodId());
            if (paymentMethod == null || !userId.equals(paymentMethod.getUserId())) {
                throw inconsistentData();
            }
        }
        return new PaymentDetailResponse.PaymentAttemptResponse(
            attempt.getAttemptSequence(),
            attempt.getRequestedAmount(),
            attempt.getRequestedAt(),
            attempt.getRespondedAt(),
            attempt.getResult(),
            paymentMethod == null ? null : paymentMethod.getCardCompany(),
            paymentMethod == null ? null : paymentMethod.getMaskedCardNumber()
        );
    }

    private RefundListResponse.RefundItemResponse toRefundListItem(Long subscriptionId, Refund refund) {
        validateRefund(subscriptionId, refund);
        return new RefundListResponse.RefundItemResponse(
            refund.getPublicId(),
            refund.getRefundType(),
            refund.getStatus(),
            refund.getRefundAmount(),
            refund.getSuccessfulRefundAmount(),
            refund.getUnprocessedAmount(),
            refund.getRequestedAt(),
            refund.getCompletedAt()
        );
    }

    private RefundDetailResponse.CancellationResponse toCancellation(
        Long userId,
        Long subscriptionId,
        Long refundId,
        PaymentTransaction cancellation,
        Map<Long, PaymentTransaction> originals
    ) {
        if (!refundId.equals(cancellation.getRefundId())
            || !userId.equals(cancellation.getUserId())
            || !subscriptionId.equals(cancellation.getSubscriptionId())
            || !isCancellation(cancellation.getTransactionType())) {
            throw inconsistentData();
        }
        Long originalId = requirePositive(cancellation.getOriginalPaymentTransactionId(), "원 결제 참조");
        PaymentTransaction original = originals.get(originalId);
        if (original == null
            || !userId.equals(original.getUserId())
            || !subscriptionId.equals(original.getSubscriptionId())
            || !Objects.equals(cancellation.getSubscriptionPeriodId(), original.getSubscriptionPeriodId())
            || !isOriginalPayment(original.getTransactionType())
            || original.getStatus() != PaymentTransactionStatus.SUCCESS) {
            throw inconsistentData();
        }
        return new RefundDetailResponse.CancellationResponse(
            cancellation.getPublicId(),
            original.getPublicId(),
            cancellation.getStatus(),
            cancellation.getTransactionAmount(),
            cancellation.getOccurredAt()
        );
    }

    private Map<Long, PaymentMethod> loadPaymentMethods(List<PaymentAttempt> attempts) {
        Set<Long> ids = attempts.stream()
            .map(PaymentAttempt::getPaymentMethodId)
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return paymentMethodRepository.findAllById(ids)
            .stream()
            .collect(Collectors.toMap(PaymentMethod::getId, Function.identity()));
    }

    private Map<Long, PaymentTransaction> loadOriginalPayments(List<PaymentTransaction> cancellations) {
        Set<Long> ids = cancellations.stream()
            .map(PaymentTransaction::getOriginalPaymentTransactionId)
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return paymentTransactionRepository.findAllById(ids)
            .stream()
            .collect(Collectors.toMap(PaymentTransaction::getId, Function.identity()));
    }

    private void validateRefund(Long subscriptionId, Refund refund) {
        if (!subscriptionId.equals(refund.getSubscriptionId())) {
            throw inconsistentData();
        }
    }

    private boolean isOriginalPayment(PaymentTransactionType type) {
        return type == PaymentTransactionType.FIRST_SUBSCRIPTION_PAYMENT
            || type == PaymentTransactionType.REGULAR_PAYMENT
            || type == PaymentTransactionType.SETTING_CHANGE_PAYMENT;
    }

    private boolean isCancellation(PaymentTransactionType type) {
        return type == PaymentTransactionType.SETTING_CHANGE_PARTIAL_CANCELLATION
            || type == PaymentTransactionType.CANCELLATION_BEFORE_START
            || type == PaymentTransactionType.NEXT_PERIOD_FULL_CANCELLATION
            || type == PaymentTransactionType.DELIVERY_PARTIAL_CANCELLATION;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 식별자는 양수여야 합니다.");
        }
    }

    private void validatePaymentId(String paymentId) {
        if (!PublicIdFormat.isUuidV4(paymentId)) {
            throw new IllegalArgumentException("유효하지 않은 결제 공개 식별자입니다.");
        }
    }

    private void validateRefundId(String refundId) {
        if (!PublicIdFormat.isUuidV4(refundId)) {
            throw new IllegalArgumentException("유효하지 않은 환불 공개 식별자입니다.");
        }
    }

    private Long requirePositive(Long value, String target) {
        if (value == null || value <= 0) {
            throw new IllegalStateException(target + " 데이터가 올바르지 않습니다.");
        }
        return value;
    }

    private IllegalStateException inconsistentData() {
        return new IllegalStateException("결제·환불 이력 기준 데이터가 올바르지 않습니다.");
    }
}
