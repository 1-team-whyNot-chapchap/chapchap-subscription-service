package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class PaymentProviderAuthenticationFailedException extends BusinessException {

    public PaymentProviderAuthenticationFailedException() {
        super(ErrorCode.PAYMENT_PROVIDER_AUTHENTICATION_FAILED);
    }
}
