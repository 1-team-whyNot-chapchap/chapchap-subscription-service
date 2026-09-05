package com.chapchap.subscription.global.exception.subscription;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 이용 중이 아닌 구독의 설정 변경을 차단한다. */
public class SubscriptionChangeNotAllowedException extends BusinessException {

    public SubscriptionChangeNotAllowedException() {
        super(ErrorCode.SUBSCRIPTION_CHANGE_NOT_ALLOWED);
    }
}
