package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class PaymentMethodInvalidException extends BusinessException{
    public PaymentMethodInvalidException() {
        super(ErrorCode.PAYMENT_METHOD_INVALID);
    }
}
