package com.chapchap.subscription.global.exception.order;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 주문이 없거나 인증 고객의 소유가 아닌 경우 존재 여부를 구분하지 않고 사용한다. */
public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException() {
        super(ErrorCode.ORDER_NOT_FOUND);
    }
}
