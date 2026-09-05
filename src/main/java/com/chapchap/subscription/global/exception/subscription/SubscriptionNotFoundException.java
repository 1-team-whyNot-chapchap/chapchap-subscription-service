package com.chapchap.subscription.global.exception.subscription;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 설정 변경·해지 대상인 고객 구독이 없을 때 발생한다. */
public class SubscriptionNotFoundException extends BusinessException {

    public SubscriptionNotFoundException() {
        super(ErrorCode.SUBSCRIPTION_NOT_FOUND);
    }
}
