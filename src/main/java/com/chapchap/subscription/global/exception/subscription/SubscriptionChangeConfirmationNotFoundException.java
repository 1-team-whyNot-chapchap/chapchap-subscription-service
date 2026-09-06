package com.chapchap.subscription.global.exception.subscription;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class SubscriptionChangeConfirmationNotFoundException extends BusinessException {
    public SubscriptionChangeConfirmationNotFoundException() {
        super(ErrorCode.SUBSCRIPTION_CHANGE_CONFIRMATION_NOT_FOUND);
    }
}
