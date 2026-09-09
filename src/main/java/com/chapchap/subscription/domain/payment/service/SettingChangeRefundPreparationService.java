package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionStatus;
import com.chapchap.subscription.domain.payment.entity.PaymentTransactionType;
import com.chapchap.subscription.domain.payment.entity.Refund;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.repository.RefundRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.domain.subscription.service.SettingChangeAmountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SettingChangeRefundPreparationService {
    private final SettingChangeAmountService amounts;
    private final RefundRepository refunds;
    private final PaymentTransactionRepository payments;
    private final SubscriptionPeriodRepository periods;
    private final KstReferenceTimeProvider time;

    public SettingChangeRefundPreparationService(SettingChangeAmountService amounts,
        RefundRepository refunds, PaymentTransactionRepository payments,
        SubscriptionPeriodRepository periods, KstReferenceTimeProvider time) {
        this.amounts = amounts;
        this.refunds = refunds;
        this.payments = payments;
        this.periods = periods;
        this.time = time;
    }

    @Transactional
    public PreparedSettingChangeRefund prepareNext(Long settingId) {
        var snapshot = amounts.analyze(settingId);
        long reduction = snapshot.oldAmount() - snapshot.newAmount();
        var existing = refunds.findBySubscriptionSettingId(settingId);
        if (existing.isEmpty() && reduction <= 0) {
            throw new IllegalStateException("Setting change does not require a refund");
        }
        Refund refund = existing.orElseGet(() -> refunds.saveAndFlush(Refund.createSettingChangeReduction(
            snapshot.setting().getSubscriptionId(), settingId, reduction)));
        if (refund.getStatus() != com.chapchap.subscription.domain.payment.entity.RefundStatus.PENDING) {
            return new PreparedSettingChangeRefund(refund.getId(), refund.getPublicId(), refund.getStatus(), null);
        }
        long remaining = refund.getRefundAmount() - refund.getSuccessfulRefundAmount();
        Map<Long, Long> byOriginal = new HashMap<>();
        for (PaymentAllocation allocation : snapshot.oldAllocations()) {
            if (allocation.currentCancelableAmount() > 0) byOriginal.merge(
                allocation.getOriginalPaymentTransactionId(), allocation.currentCancelableAmount(), Math::addExact);
        }
        Map<Long, PaymentTransaction> originals = new HashMap<>();
        payments.findAllById(byOriginal.keySet()).forEach(value -> originals.put(value.getId(), value));
        PaymentTransaction original = originals.values().stream().sorted(Comparator
            .comparingInt((PaymentTransaction value) -> value.getTransactionType() == PaymentTransactionType.SETTING_CHANGE_PAYMENT ? 0 : 1)
            .thenComparing(PaymentTransaction::getOccurredAt, Comparator.reverseOrder())
            .thenComparing(PaymentTransaction::getId, Comparator.reverseOrder())).findFirst().orElseThrow();
        long cancelAmount = Math.min(remaining, byOriginal.get(original.getId()));
        if (!original.getSubscriptionId().equals(snapshot.setting().getSubscriptionId())
            || original.getStatus() != PaymentTransactionStatus.SUCCESS
            || original.getCancelableAmount() == null || original.getCancelableAmount() < cancelAmount) {
            throw new IllegalStateException("Original payment is not cancellable");
        }
        var period = periods.findById(original.getSubscriptionPeriodId()).orElseThrow();
        var now = time.now();
        PaymentTransaction cancellation = payments.saveAndFlush(PaymentTransaction.createSettingChangeCancellation(
            original.getUserId(), original.getSubscriptionId(), period.getId(), settingId, refund.getId(),
            original.getId(), cancelAmount, now, period.getPeriodStartDate(), period.getPeriodEndDate(),
            snapshot.setting().getEffectiveStartDate(), UUID.randomUUID().toString().replace("-", ""), now));
        return new PreparedSettingChangeRefund(refund.getId(), refund.getPublicId(), refund.getStatus(), cancellation.getId());
    }
}
