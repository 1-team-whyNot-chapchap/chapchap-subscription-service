package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class CurrentPaymentMethodDeleteNotAllowedException extends BusinessException {

    public CurrentPaymentMethodDeleteNotAllowedException() {
        super(ErrorCode.CURRENT_PAYMENT_METHOD_DELETE_NOT_ALLOWED);
    }
}
