package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class PaymentMethodNotFoundException extends BusinessException {

    public PaymentMethodNotFoundException() {
        super(ErrorCode.PAYMENT_METHOD_NOT_FOUND);
    }
}
