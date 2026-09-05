package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 결제 거래가 처리 중이어서 구독 해지·취소를 확정할 수 없을 때 사용한다. */
public class PaymentTransactionProcessingException extends BusinessException {

    public PaymentTransactionProcessingException() {
        super(ErrorCode.PAYMENT_TRANSACTION_PROCESSING);
    }
}
