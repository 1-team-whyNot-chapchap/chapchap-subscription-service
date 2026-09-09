package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 외부 결제 제공자가 첫 결제 실패를 명확히 확정한 경우 발생한다. */
public class PaymentDeclinedException extends BusinessException {

    public PaymentDeclinedException() {
        super(ErrorCode.PAYMENT_DECLINED);
    }
}
