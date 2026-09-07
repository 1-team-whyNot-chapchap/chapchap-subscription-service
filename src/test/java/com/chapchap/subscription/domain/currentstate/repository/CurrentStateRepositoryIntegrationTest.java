package com.chapchap.subscription.domain.currentstate.repository;

import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CurrentStateRepositoryIntegrationTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 9, 7, 9, 0);
    private static final List<PaymentTransactionType> ORIGINAL_PAYMENT_TYPES = List.of(
        PaymentTransactionType.FIRST_SUBSCRIPTION_PAYMENT,
        PaymentTransactionType.REGULAR_PAYMENT,
        PaymentTransactionType.SETTING_CHANGE_PAYMENT
    );

    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void 현재_결제는_취소_거래를_제외하고_동일_발생시각이면_큰_id를_선택한다() {
        long userId = uniquePositiveId();
        long subscriptionId = uniquePositiveId();
        long periodId = uniquePositiveId();
        PaymentTransaction first = paymentTransactionRepository.saveAndFlush(
            PaymentTransaction.createFirstSubscriptionPayment(
                userId, subscriptionId, periodId, 10_000L, OCCURRED_AT,
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 10, 5),
                uniqueKey("first"), OCCURRED_AT
            )
        );
        PaymentTransaction regular = paymentTransactionRepository.saveAndFlush(
            PaymentTransaction.createRegularPayment(
                userId, subscriptionId, uniquePositiveId(), 20_000L, OCCURRED_AT,
                LocalDate.of(2026, 10, 6), LocalDate.of(2026, 11, 2),
                uniqueKey("regular"), OCCURRED_AT
            )
        );
        PaymentTransaction cancellation = paymentTransactionRepository.saveAndFlush(
            PaymentTransaction.createCancellation(
                userId, subscriptionId, periodId, uniquePositiveId(), first.getId(),
                PaymentTransactionType.CANCELLATION_BEFORE_START, 10_000L, OCCURRED_AT.plusHours(1),
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 10, 5),
                uniqueKey("cancel"), OCCURRED_AT.plusHours(1)
            )
        );
        entityManager.clear();

        PaymentTransaction found = paymentTransactionRepository
            .findFirstByUserIdAndTransactionTypeInOrderByOccurredAtDescIdDesc(userId, ORIGINAL_PAYMENT_TYPES)
            .orElseThrow();

        assertThat(found.getId()).isEqualTo(regular.getId());
        assertThat(found.getTransactionType()).isEqualTo(PaymentTransactionType.REGULAR_PAYMENT);
        assertThat(found.getId()).isNotEqualTo(cancellation.getId());
    }

    @Test
    void 최근_환불은_다른_구독을_제외하고_동일_요청시각이면_큰_id를_선택한다() {
        long subscriptionId = uniquePositiveId();
        Refund older = refundRepository.saveAndFlush(
            Refund.createSettingChangeReduction(subscriptionId, uniquePositiveId(), 10_000L)
        );
        Refund newer = refundRepository.saveAndFlush(
            Refund.createSettingChangeReduction(subscriptionId, uniquePositiveId(), 20_000L)
        );
        Refund otherSubscription = refundRepository.saveAndFlush(
            Refund.createSettingChangeReduction(uniquePositiveId(), uniquePositiveId(), 30_000L)
        );
        LocalDateTime sameRequestedAt = LocalDateTime.of(2026, 9, 7, 13, 55);
        entityManager.createNativeQuery("UPDATE refunds SET requested_at = :requestedAt WHERE id IN (:olderId, :newerId)")
            .setParameter("requestedAt", sameRequestedAt)
            .setParameter("olderId", older.getId())
            .setParameter("newerId", newer.getId())
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Refund found = refundRepository
            .findFirstBySubscriptionIdOrderByRequestedAtDescIdDesc(subscriptionId)
            .orElseThrow();

        assertThat(found.getId()).isEqualTo(newer.getId());
        assertThat(found.getRefundAmount()).isEqualTo(20_000L);
        assertThat(found.getId()).isNotEqualTo(otherSubscription.getId());
    }

    private String uniqueKey(String prefix) {
        return prefix + "-" + uniquePositiveId();
    }

    private long uniquePositiveId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }
}
