package com.chapchap.subscription.domain.payment.service.exception;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 외부 결제를 시작할 현재 사용 가능한 자동결제수단이 없는 경우 발생한다. */
public class CurrentPaymentMethodUnavailableException extends BusinessException {
    /** 첫 결제에 사용할 현재 자동결제수단이 없는 예외를 생성한다. */
    public CurrentPaymentMethodUnavailableException() {
        super(ErrorCode.CURRENT_PAYMENT_METHOD_REQUIRED);
    }
}
