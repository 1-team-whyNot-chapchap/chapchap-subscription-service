package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class PaymentMethodRegistrationConflictException extends BusinessException {

    public PaymentMethodRegistrationConflictException() {
        super(ErrorCode.PAYMENT_METHOD_REGISTRATION_CONFLICT);
    }
}
