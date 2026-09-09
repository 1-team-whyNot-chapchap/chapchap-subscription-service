package com.chapchap.subscription.global.exception.subscription;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

/** 주문 전달 이벤트가 Kafka에 완료 저장된 기간의 시작 전 취소를 차단한다. */
public class SubscriptionKafkaDeliveryCompletedException extends BusinessException {

    public SubscriptionKafkaDeliveryCompletedException() {
        super(ErrorCode.SUBSCRIPTION_KAFKA_DELIVERY_COMPLETED);
    }
}
