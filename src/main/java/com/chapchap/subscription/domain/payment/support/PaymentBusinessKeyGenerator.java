package com.chapchap.subscription.domain.payment.support;

/**
 * 동일한 결제·취소 업무의 거래 행이 중복 생성되지 않도록 내부 업무 키를 생성한다.
 *
 * <p>이 키는 DB의 업무 거래 중복 방지용이며, 외부 PG 요청의 멱등성 키와 목적이 다르다.</p>
 */
public final class PaymentBusinessKeyGenerator {
    private static final String FIRST_PAYMENT_PREFIX = "PAYMENT:FIRST:";
    private static final String REGULAR_PAYMENT_PREFIX = "PAYMENT:REGULAR:";
    private static final String SETTING_CHANGE_PREFIX = "PAYMENT:CHANGE:";
    private static final String CANCELLATION_PREFIX = "CANCEL:";

    private PaymentBusinessKeyGenerator() {
    }

    /**
     * 첫 이용 기간에 대한 첫 구독 결제 업무 키를 생성한다.
     *
     * @param subscriptionPeriodId 첫 결제 대상 이용 기간 식별자
     * @return {@code PAYMENT:FIRST:{이용기간 식별자}} 형식의 키
     */
    public static String firstPayment(Long subscriptionPeriodId) {
        return FIRST_PAYMENT_PREFIX + requirePositive(subscriptionPeriodId, "subscriptionPeriodId");
    }

    /**
     * 다음 이용 기간에 대한 정기결제 업무 키를 생성한다.
     *
     * @param subscriptionPeriodId 정기결제 대상 이용 기간 식별자
     * @return {@code PAYMENT:REGULAR:{이용기간 식별자}} 형식의 키
     */
    public static String regularPayment(Long subscriptionPeriodId) {
        return REGULAR_PAYMENT_PREFIX + requirePositive(subscriptionPeriodId, "subscriptionPeriodId");
    }

    /**
     * 특정 구독 설정 버전의 추가 결제 업무 키를 생성한다.
     *
     * @param subscriptionSettingId 추가 결제를 발생시킨 설정 버전 식별자
     * @return {@code PAYMENT:CHANGE:{설정버전 식별자}} 형식의 키
     */
    public static String settingChange(Long subscriptionSettingId) {
        return SETTING_CHANGE_PREFIX + requirePositive(subscriptionSettingId, "subscriptionSettingId");
    }

    /**
     * 하나의 환불에서 같은 원 결제를 중복 취소하지 않도록 취소 업무 키를 생성한다.
     *
     * @param refundId 환불 업무 식별자
     * @param originalPaymentTransactionId 취소 대상 원 결제 거래 식별자
     * @return {@code CANCEL:{환불 식별자}:{원결제 거래 식별자}} 형식의 키
     */
    public static String cancellation(Long refundId, Long originalPaymentTransactionId) {
        return CANCELLATION_PREFIX
            + requirePositive(refundId, "refundId")
            + ":"
            + requirePositive(originalPaymentTransactionId, "originalPaymentTransactionId");
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
