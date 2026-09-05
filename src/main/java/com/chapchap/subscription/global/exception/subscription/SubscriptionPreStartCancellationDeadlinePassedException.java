package com.chapchap.subscription.global.exception.subscription;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 시작 전 취소 마감 시각 이후 요청을 차단한다. */
public class SubscriptionPreStartCancellationDeadlinePassedException extends BusinessException {

    public SubscriptionPreStartCancellationDeadlinePassedException() {
        super(ErrorCode.SUBSCRIPTION_PRE_START_CANCELLATION_DEADLINE_PASSED);
    }
}
