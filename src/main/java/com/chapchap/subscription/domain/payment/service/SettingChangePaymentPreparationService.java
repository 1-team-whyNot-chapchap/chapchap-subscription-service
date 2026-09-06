package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.support.PaymentBusinessKeyGenerator;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionPeriodRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.domain.subscription.service.SettingChangeAmountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class SettingChangePaymentPreparationService {
    private final SettingChangeAmountService amounts;
    private final PaymentTransactionRepository payments;
    private final SubscriptionPeriodRepository periods;
    private final KstReferenceTimeProvider time;
    private final SubscriptionSettingRepository settings;

    public SettingChangePaymentPreparationService(SettingChangeAmountService amounts,
        PaymentTransactionRepository payments,
        SubscriptionPeriodRepository periods, KstReferenceTimeProvider time,
        SubscriptionSettingRepository settings) {
        this.amounts = amounts;
        this.payments = payments;
        this.periods = periods;
        this.time = time;
        this.settings = settings;
    }

    @Transactional
    public PaymentTransaction prepare(Long userId, Long settingId) {
        settings.findWithLockById(settingId).orElseThrow();
        String businessKey = PaymentBusinessKeyGenerator.settingChange(settingId);
        var existing = payments.findByBusinessDeduplicationKey(businessKey);
        if (existing.isPresent()) {
            throw new com.chapchap.subscription.global.exception.subscription.SubscriptionChangeConfirmationNotFoundException();
        }
        var snapshot = amounts.analyze(settingId);
        if (snapshot.newAmount() <= snapshot.oldAmount()) {
            throw new IllegalStateException("Pending setting change does not require an additional payment");
        }
        var firstOrder = snapshot.newOrders().stream().findFirst().orElseThrow();
        var period = periods.findById(firstOrder.getSubscriptionPeriodId()).orElseThrow();
        var now = time.now();
        return payments.save(PaymentTransaction.createSettingChangePayment(
            userId, snapshot.setting().getSubscriptionId(), period.getId(), settingId,
            snapshot.newAmount() - snapshot.oldAmount(), now, period.getPeriodStartDate(),
            period.getPeriodEndDate(), snapshot.setting().getEffectiveStartDate(),
            "SETTING-CHANGE-" + UUID.randomUUID(), now
        ));
    }
}
