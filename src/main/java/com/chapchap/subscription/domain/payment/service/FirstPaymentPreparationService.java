package com.chapchap.subscription.domain.payment.service;

import com.chapchap.subscription.domain.payment.entity.PaymentTransaction;
import com.chapchap.subscription.domain.payment.repository.PaymentTransactionRepository;
import com.chapchap.subscription.domain.payment.service.command.FirstPaymentPrepareCommand;
import com.chapchap.subscription.domain.payment.service.result.PreparedFirstPayment;
import com.chapchap.subscription.domain.payment.support.PaymentBusinessKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 외부 PG 호출 전에 첫 결제 거래를 처리 중 상태로 준비한다. */
@Service
public class FirstPaymentPreparationService {
    private static final String EXTERNAL_REQUEST_KEY_PREFIX = "FIRST-PAYMENT-";

    private final PaymentTransactionRepository paymentTransactionRepository;

    /**
     * 결제 거래 저장소를 사용해 첫 결제 준비 Service를 구성한다.
     *
     * @param paymentTransactionRepository 결제 거래 저장소
     */
    public FirstPaymentPreparationService(PaymentTransactionRepository paymentTransactionRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    /**
     * 같은 이용 기간의 첫 결제 거래를 한 번만 생성한다.
     *
     * <p>기존 거래가 있으면 상태와 관계없이 그 거래를 반환한다. 첫 결제 실패 뒤 재신청은
     * 다음 순번의 이용 기간을 사용하므로 새 업무 키와 새 거래를 갖는다.</p>
     *
     * @param command 구독·이용 기간·금액·처리 기준 시각이 확정된 준비 입력
     * @return 새로 생성했거나 같은 업무 키로 이미 존재하는 첫 결제 거래 정보
     */
    @Transactional
    public PreparedFirstPayment prepare(FirstPaymentPrepareCommand command) {
        String businessKey = PaymentBusinessKeyGenerator.firstPayment(command.subscriptionPeriodId());

        return paymentTransactionRepository.findByBusinessDeduplicationKey(businessKey)
            .map(transaction -> PreparedFirstPayment.from(transaction, false))
            .orElseGet(() -> createPaymentTransaction(command));
    }

    private PreparedFirstPayment createPaymentTransaction(FirstPaymentPrepareCommand command) {
        PaymentTransaction transaction = PaymentTransaction.createFirstSubscriptionPayment(
            command.userId(),
            command.subscriptionId(),
            command.subscriptionPeriodId(),
            command.transactionAmount(),
            command.processingReferenceAt(),
            command.periodStartDate(),
            command.periodEndDate(),
            EXTERNAL_REQUEST_KEY_PREFIX + UUID.randomUUID(),
            command.processingReferenceAt()
        );

        return PreparedFirstPayment.from(paymentTransactionRepository.save(transaction), true);
    }
}
