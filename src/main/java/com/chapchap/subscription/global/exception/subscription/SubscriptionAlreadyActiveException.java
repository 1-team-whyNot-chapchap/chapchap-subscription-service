package com.chapchap.subscription.global.exception.subscription;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 고객에게 이미 진행 중인 구독이 있어 새 첫 구독 신청을 시작할 수 없는 경우 발생한다. */
public class SubscriptionAlreadyActiveException extends BusinessException {

    /** API 명세의 {@code SUBSCRIPTION_002} 오류로 변환될 업무 예외를 생성한다. */
    public SubscriptionAlreadyActiveException() {
        super(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
    }
}
