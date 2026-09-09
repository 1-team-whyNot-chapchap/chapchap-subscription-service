package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 환불이 없거나 인증 고객의 구독에 속하지 않을 때 존재 여부를 구분하지 않고 사용한다. */
public class RefundHistoryNotFoundException extends BusinessException {
    public RefundHistoryNotFoundException() {
        super(ErrorCode.REFUND_HISTORY_NOT_FOUND);
    }
}
