package com.chapchap.subscription.domain.payment.repository;

import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocationType;
import com.chapchap.subscription.domain.payment.entity.PaymentAttempt;
import com.chapchap.subscription.domain.payment.entity.PaymentAttemptResult;
import com.chapchap.subscription.domain.payment.entity.PaymentMethod;
import com.chapchap.subscription.domain.payment.entity.PaymentMethodStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentProviderCode;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.Refund;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class PaymentRepositoryIntegrationTest {
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 9, 4, 10, 0);
    private static final LocalDateTime RESPONDED_AT = REQUESTED_AT.plusSeconds(1);

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 같은_업무_중복방지_키의_거래는_MySQL_UNIQUE_제약으로_거절된다() {
        long subscriptionPeriodId = uniquePositiveId();
        paymentTransactionRepository.saveAndFlush(transaction(subscriptionPeriodId, uniqueKey("external")));

        assertThatThrownBy(() -> paymentTransactionRepository.saveAndFlush(
            transaction(subscriptionPeriodId, uniqueKey("external"))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 정기결제_거래는_재시도대기와_새_멱등성키의_처리중_상태를_MySQL에_보존한다() {
        long periodId = uniquePositiveId();
        String firstKey = uniqueKey("regular-initial");
        PaymentTransaction transaction = paymentTransactionRepository.saveAndFlush(
            PaymentTransaction.createRegularPayment(
                uniquePositiveId(), uniquePositiveId(), periodId, 100_000L, REQUESTED_AT,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28), firstKey, REQUESTED_AT
            )
        );

        transaction.waitForRegularPaymentRetry();
        paymentTransactionRepository.saveAndFlush(transaction);
        entityManager.clear();

        PaymentTransaction waiting = paymentTransactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus.RETRY_WAITING);
        assertThat(waiting.getExternalRequestIdempotencyKey()).isNull();

        String retryKey = uniqueKey("regular-retry");
        waiting.startRegularPaymentRetry(retryKey);
        paymentTransactionRepository.saveAndFlush(waiting);
        entityManager.clear();

        PaymentTransaction retrying = paymentTransactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(retrying.getStatus()).isEqualTo(com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus.PROCESSING);
        assertThat(retrying.getExternalRequestIdempotencyKey()).isEqualTo(retryKey);
        assertThat(retrying.getBusinessDeduplicationKey()).isEqualTo("PAYMENT:REGULAR:" + periodId);
    }

    @Test
    void 처리_중_거래의_같은_외부요청_멱등성_키는_MySQL_UNIQUE_제약으로_거절된다() {
        String externalRequestIdempotencyKey = uniqueKey("external");
        paymentTransactionRepository.saveAndFlush(
            transaction(uniquePositiveId(), externalRequestIdempotencyKey)
        );

        assertThatThrownBy(() -> paymentTransactionRepository.saveAndFlush(
            transaction(uniquePositiveId(), externalRequestIdempotencyKey)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_거래와_시도순번의_결제시도는_MySQL_UNIQUE_제약으로_거절된다() {
        long transactionId = savedTransaction().getId();
        paymentAttemptRepository.saveAndFlush(successAttempt(transactionId, 1, uniqueKey("attempt")));

        assertThatThrownBy(() -> paymentAttemptRepository.saveAndFlush(
            successAttempt(transactionId, 1, uniqueKey("attempt"))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_멱등성_키의_결제시도는_MySQL_UNIQUE_제약으로_거절된다() {
        String idempotencyKey = uniqueKey("attempt");
        paymentAttemptRepository.saveAndFlush(
            successAttempt(savedTransaction().getId(), 1, idempotencyKey)
        );

        assertThatThrownBy(() -> paymentAttemptRepository.saveAndFlush(
            successAttempt(savedTransaction().getId(), 1, idempotencyKey)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_주문과_원결제의_배분은_MySQL_UNIQUE_제약으로_거절된다() {
        long orderId = uniquePositiveId();
        long transactionId = savedTransaction().getId();
        paymentAllocationRepository.saveAndFlush(allocation(orderId, transactionId, 10_000L));

        assertThatThrownBy(() -> paymentAllocationRepository.saveAndFlush(
            allocation(orderId, transactionId, 10_000L)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 취소가능_배분금액_생성_컬럼은_MySQL에서_계산된다() {
        PaymentAllocation allocation = paymentAllocationRepository.saveAndFlush(
            allocation(uniquePositiveId(), savedTransaction().getId(), 10_000L)
        );

        entityManager.refresh(allocation);

        assertThat(allocation.getCancelableAmount()).isEqualTo(10_000L);
    }

    @Test
    void 정상_거래와_결제시도와_배분을_저장하고_조회할_수_있다() {
        PaymentTransaction transaction = savedTransaction();
        PaymentAttempt attempt = paymentAttemptRepository.saveAndFlush(
            successAttempt(transaction.getId(), 1, uniqueKey("attempt"))
        );
        PaymentAllocation allocation = paymentAllocationRepository.saveAndFlush(
            allocation(uniquePositiveId(), transaction.getId(), 10_000L)
        );

        PaymentTransaction foundTransaction = paymentTransactionRepository
            .findByBusinessDeduplicationKey(transaction.getBusinessDeduplicationKey())
            .orElseThrow();
        PaymentAttempt foundAttempt = paymentAttemptRepository
            .findAllByPaymentTransactionIdOrderByAttemptSequenceAsc(transaction.getId())
            .getFirst();
        PaymentAllocation foundAllocation = paymentAllocationRepository
            .findAllByOriginalPaymentTransactionIdOrderByIdAsc(transaction.getId())
            .getFirst();

        assertThat(foundTransaction.getId()).isEqualTo(transaction.getId());
        assertThat(foundAttempt.getId()).isEqualTo(attempt.getId());
        assertThat(foundAttempt.getResult()).isEqualTo(PaymentAttemptResult.SUCCESS);
        assertThat(foundAllocation.getId()).isEqualTo(allocation.getId());
        assertThat(foundAllocation.getAllocatedAmount()).isEqualTo(10_000L);
    }

    @Test
    void 현재_자동결제수단을_DELETED_상태로_변경해_이력을_보존한다() {
        long userId = uniquePositiveId();
        PaymentMethod paymentMethod = paymentMethodRepository.saveAndFlush(
            PaymentMethod.createAsCurrent(
                userId,
                PaymentProviderCode.PORTONE,
                uniqueKey("protected-method"),
                "테스트카드",
                "****-****-****-1234",
                REQUESTED_AT
            )
        );
        String protectedExternalMethodRef = paymentMethod.getProtectedExternalMethodRef();
        LocalDateTime lastSelectedAt = paymentMethod.getLastSelectedAt();

        paymentMethod.markAsDeleted(RESPONDED_AT);
        paymentMethodRepository.flush();
        entityManager.refresh(paymentMethod);

        assertThat(paymentMethod.getStatus()).isEqualTo(PaymentMethodStatus.DELETED);
        assertThat(paymentMethod.isCurrent()).isFalse();
        assertThat(paymentMethod.getCurrentUserId()).isNull();
        assertThat(paymentMethod.getRetirementAt()).isEqualTo(RESPONDED_AT);
        assertThat(paymentMethod.getDeletedAt()).isNull();
        assertThat(paymentMethod.getLastSelectedAt()).isEqualTo(lastSelectedAt);
        assertThat(paymentMethod.getProtectedExternalMethodRef()).isEqualTo(protectedExternalMethodRef);
    }

    @Test
    void 설정변경_추가결제와_감액환불과_부분취소거래를_MySQL에_저장한다() {
        long userId = uniquePositiveId();
        long subscriptionId = uniquePositiveId();
        long periodId = uniquePositiveId();
        long settingId = uniquePositiveId();
        PaymentTransaction additional = paymentTransactionRepository.saveAndFlush(
            PaymentTransaction.createSettingChangePayment(userId, subscriptionId, periodId, settingId,
                3_000L, REQUESTED_AT, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28),
                LocalDate.of(2026, 9, 8), uniqueKey("setting-payment"), REQUESTED_AT)
        );
        Refund refund = refundRepository.saveAndFlush(
            Refund.createSettingChangeReduction(subscriptionId, uniquePositiveId(), 1_000L)
        );
        PaymentTransaction cancellation = paymentTransactionRepository.saveAndFlush(
            PaymentTransaction.createSettingChangeCancellation(userId, subscriptionId, periodId,
                refund.getSubscriptionSettingId(), refund.getId(), additional.getId(), 1_000L,
                REQUESTED_AT, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 28),
                LocalDate.of(2026, 9, 8), uniqueKey("setting-cancel"), REQUESTED_AT)
        );

        assertThat(additional.getSubscriptionSettingId()).isEqualTo(settingId);
        assertThat(refund.getSubscriptionSettingId()).isNotNull();
        assertThat(cancellation.getSubscriptionSettingId()).isEqualTo(refund.getSubscriptionSettingId());
    }

    private PaymentTransaction savedTransaction() {
        return paymentTransactionRepository.saveAndFlush(
            transaction(uniquePositiveId(), uniqueKey("external"))
        );
    }

    private PaymentTransaction transaction(long subscriptionPeriodId, String externalRequestIdempotencyKey) {
        return PaymentTransaction.createFirstSubscriptionPayment(
            uniquePositiveId(),
            uniquePositiveId(),
            subscriptionPeriodId,
            10_000L,
            REQUESTED_AT,
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 9, 13),
            externalRequestIdempotencyKey,
            REQUESTED_AT
        );
    }

    private PaymentAttempt successAttempt(long transactionId, int sequence, String idempotencyKey) {
        return PaymentAttempt.success(
            transactionId,
            uniquePositiveId(),
            PaymentProviderCode.PORTONE,
            sequence,
            idempotencyKey,
            10_000L,
            REQUESTED_AT,
            RESPONDED_AT,
            uniqueKey("payment"),
            uniqueKey("transaction"),
            "PAID"
        );
    }

    private PaymentAllocation allocation(long orderId, long transactionId, long amount) {
        return PaymentAllocation.create(
            orderId,
            transactionId,
            PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT,
            amount
        );
    }

    private String uniqueKey(String prefix) {
        return prefix + "-" + uniquePositiveId();
    }

    private long uniquePositiveId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }
}
