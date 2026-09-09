package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 결제 거래가 없거나 인증 고객 소유가 아닐 때 존재 여부를 구분하지 않고 사용한다. */
public class PaymentHistoryNotFoundException extends BusinessException {
    public PaymentHistoryNotFoundException() {
        super(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);
    }
}
