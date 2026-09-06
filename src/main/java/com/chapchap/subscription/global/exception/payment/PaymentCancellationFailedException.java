package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class PaymentCancellationFailedException extends BusinessException {
    public PaymentCancellationFailedException() {
        super(ErrorCode.PAYMENT_CANCELLATION_FAILED);
    }
}
