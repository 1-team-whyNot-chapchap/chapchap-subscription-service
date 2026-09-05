package com.chapchap.subscription.global.exception.subscription;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 현재 구독 상태에서 해지 또는 시작 전 취소를 요청할 수 없을 때 사용한다. */
public class SubscriptionCancellationNotAllowedException extends BusinessException {

    public SubscriptionCancellationNotAllowedException() {
        super(ErrorCode.SUBSCRIPTION_CANCELLATION_NOT_ALLOWED);
    }
}
