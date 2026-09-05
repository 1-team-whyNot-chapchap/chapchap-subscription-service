package com.chapchap.subscription.global.exception.subscription;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 마지막 설정 변경이 아직 확정되지 않아 새 변경을 접수할 수 없을 때 발생한다. */
public class SubscriptionChangeInProgressException extends BusinessException {

    public SubscriptionChangeInProgressException() {
        super(ErrorCode.SUBSCRIPTION_CHANGE_IN_PROGRESS);
    }
}
