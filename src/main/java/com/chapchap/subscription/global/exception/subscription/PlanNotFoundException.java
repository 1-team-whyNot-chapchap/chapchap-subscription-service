package com.chapchap.subscription.global.exception.subscription;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 요청한 공개 식별자에 해당하는 선택 가능한 구독 플랜을 찾지 못한 경우 발생한다. */
public class PlanNotFoundException extends BusinessException {

    /** API 명세의 {@code SUBSCRIPTION_001} 오류로 변환될 업무 예외를 생성한다. */
    public PlanNotFoundException() {
        super(ErrorCode.PLAN_NOT_FOUND);
    }
}
