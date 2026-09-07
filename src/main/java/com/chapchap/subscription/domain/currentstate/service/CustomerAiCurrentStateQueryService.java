package com.chapchap.subscription.domain.currentstate.service;

import com.chapchap.subscription.domain.currentstate.response.CurrentPaymentStateResponse;
import com.chapchap.subscription.domain.currentstate.response.CurrentRefundStateResponse;
import com.chapchap.subscription.domain.currentstate.response.CurrentSubscriptionStateResponse;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Customer-AI가 요청한 고객의 현재 결제·환불·구독 업무 사실만 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerAiCurrentStateQueryService {

    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);
    private static final List<PaymentTransactionType> CURRENT_PAYMENT_TYPES = List.of(
        PaymentTransactionType.FIRST_SUBSCRIPTION_PAYMENT,
        PaymentTransactionType.REGULAR_PAYMENT,
        PaymentTransactionType.SETTING_CHANGE_PAYMENT
    );

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final RefundRepository refundRepository;
    private final SubscriptionRepository subscriptionRepository;

    public CurrentPaymentStateResponse getCurrentPayment(Long userId) {
        validateUserId(userId);
        return paymentTransactionRepository
            .findFirstByUserIdAndTransactionTypeInOrderByOccurredAtDescIdDesc(userId, CURRENT_PAYMENT_TYPES)
            .map(payment -> new CurrentPaymentStateResponse(
                payment.getStatus(),
                payment.getTransactionType(),
                payment.getTransactionAmount(),
                toKst(payment.getOccurredAt())
            ))
            .orElse(null);
    }

    public CurrentRefundStateResponse getCurrentRefund(Long userId) {
        validateUserId(userId);
        return subscriptionRepository.findByUserId(userId)
            .flatMap(subscription -> refundRepository
                .findFirstBySubscriptionIdOrderByRequestedAtDescIdDesc(requirePositiveId(subscription.getId())))
            .map(refund -> new CurrentRefundStateResponse(
                refund.getStatus(),
                refund.getRefundType(),
                refund.getRefundAmount(),
                refund.getSuccessfulRefundAmount(),
                refund.getUnprocessedAmount(),
                toKst(refund.getRequestedAt()),
                toNullableKst(refund.getCompletedAt())
            ))
            .orElse(null);
    }

    public CurrentSubscriptionStateResponse getCurrentSubscription(Long userId) {
        validateUserId(userId);
        return subscriptionRepository.findByUserId(userId)
            .map(subscription -> new CurrentSubscriptionStateResponse(subscription.getStatus()))
            .orElse(null);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 식별자는 양수여야 합니다.");
        }
    }

    private Long requirePositiveId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalStateException("구독 데이터가 올바르지 않습니다.");
        }
        return value;
    }

    private OffsetDateTime toKst(LocalDateTime value) {
        if (value == null) {
            throw new IllegalStateException("현재 상태 시각 데이터가 올바르지 않습니다.");
        }
        return value.atOffset(KST_OFFSET);
    }

    private OffsetDateTime toNullableKst(LocalDateTime value) {
        return value == null ? null : value.atOffset(KST_OFFSET);
    }
}
