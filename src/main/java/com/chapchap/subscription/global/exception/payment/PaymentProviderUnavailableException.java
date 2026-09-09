package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class PaymentProviderUnavailableException extends BusinessException {

    public PaymentProviderUnavailableException() {
        super(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
    }
}