package com.chapchap.subscription.domain.payment.client;

/** 외부 자동결제에서 명시적으로 확정된 결제 결과다. */
public enum AutomaticPaymentStatus {
    PAID,
    DECLINED,
    PROVIDER_CONFIGURATION_FAILED
}
