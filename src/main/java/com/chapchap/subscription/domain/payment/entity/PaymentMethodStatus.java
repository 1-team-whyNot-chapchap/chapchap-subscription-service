package com.chapchap.subscription.domain.payment.entity;

public enum PaymentMethodStatus {
    AVAILABLE, // 사용 가능한 자동결제수단
    DELETED, // 고객이 삭제한 결제수단
    DISCARDED // 외부 결제수단을 더 이상 사용할 수 없다고 확정한 상태
}
