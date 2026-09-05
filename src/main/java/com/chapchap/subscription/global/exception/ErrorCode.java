package com.chapchap.subscription.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // === Auth ===
    AUTHENTICATION_REQUIRED("AUTH_001", HttpStatus.UNAUTHORIZED, "인증이 필요합니다.")

    // === Payment ===
    , PAYMENT_METHOD_INVALID("PAYMENT_001", HttpStatus.BAD_REQUEST, "사용할 수 없는 자동결제수단입니다.")
    , PAYMENT_PROVIDER_AUTHENTICATION_FAILED("PAYMENT_002", HttpStatus.INTERNAL_SERVER_ERROR, "결제 서비스 연동 중 오류가 발생했습니다.")
    , PAYMENT_PROVIDER_UNAVAILABLE("PAYMENT_003", HttpStatus.BAD_GATEWAY, "결제 서비스를 일시적으로 이용할 수 없습니다.")
    , PAYMENT_METHOD_REGISTRATION_CONFLICT("PAYMENT_004", HttpStatus.CONFLICT, "자동결제수단 등록 중 상태 충돌이 발생했습니다.")
    , PAYMENT_METHOD_NOT_FOUND("PAYMENT_005", HttpStatus.NOT_FOUND, "자동결제수단을 찾을 수 없습니다.")
    , CURRENT_PAYMENT_METHOD_DELETE_NOT_ALLOWED("PAYMENT_006", HttpStatus.CONFLICT, "진행 중인 구독에 사용되는 현재 자동결제수단은 삭제할 수 없습니다.")
    , CURRENT_PAYMENT_METHOD_REQUIRED("PAYMENT_007", HttpStatus.CONFLICT, "현재 자동결제수단을 등록하거나 선택해 주세요.")
    , PAYMENT_DECLINED("PAYMENT_008", HttpStatus.CONFLICT, "결제가 승인되지 않았습니다.")
    , PAYMENT_HISTORY_NOT_FOUND("PAYMENT_011", HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다.")
    , REFUND_HISTORY_NOT_FOUND("PAYMENT_012", HttpStatus.NOT_FOUND, "환불 내역을 찾을 수 없습니다.")

    // === Address ===
    , ADDRESS_NOT_FOUND("ADDRESS_001", HttpStatus.NOT_FOUND, "배송지를 찾을 수 없습니다.")
    , ADDRESS_OUT_OF_SERVICE_AREA("ADDRESS_002", HttpStatus.BAD_REQUEST, "배송 가능 지역이 아닙니다.")
    , DEFAULT_ADDRESS_DELETE_NOT_ALLOWED("ADDRESS_003", HttpStatus.CONFLICT, "기본 배송지는 삭제할 수 없습니다.")
    , ADDRESS_IN_USE("ADDRESS_004", HttpStatus.CONFLICT, "사용 중인 배송지는 삭제할 수 없습니다.")

    // === Terms ===
    , CURRENT_REQUIRED_TERMS_NOT_FOUND("TERMS_001", HttpStatus.INTERNAL_SERVER_ERROR, "현재 적용 중인 필수 약관을 확인할 수 없습니다.")
    , TERMS_VERSION_MISMATCH("TERMS_002", HttpStatus.CONFLICT, "확인한 약관 버전이 현재 적용 약관 버전과 일치하지 않습니다.")
    , TERMS_AGREEMENT_REQUIRED("TERMS_003", HttpStatus.CONFLICT, "현재 적용 중인 필수 약관에 동의해 주세요.")

    // === Subscription ===
    , PLAN_NOT_FOUND("SUBSCRIPTION_001", HttpStatus.NOT_FOUND, "플랜을 찾을 수 없습니다.")
    , SUBSCRIPTION_ALREADY_ACTIVE("SUBSCRIPTION_002", HttpStatus.CONFLICT, "이미 진행 중인 구독이 있습니다.")
    , SUBSCRIPTION_NOT_FOUND("SUBSCRIPTION_003", HttpStatus.NOT_FOUND, "구독을 찾을 수 없습니다.")
    , SUBSCRIPTION_CHANGE_NOT_ALLOWED("SUBSCRIPTION_004", HttpStatus.CONFLICT, "현재 구독 상태에서는 설정을 변경할 수 없습니다.")
    , SUBSCRIPTION_CHANGE_IN_PROGRESS("SUBSCRIPTION_005", HttpStatus.CONFLICT, "기존 설정 변경이 처리 중입니다.")

    // === Order ===
    , ORDER_NOT_FOUND("ORDER_001", HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다.")

    // === Common ===
    , INVALID_REQUEST("COMMON_001", HttpStatus.BAD_REQUEST, "요청이 유효하지 않습니다.")
    , DATABASE_ERROR("COMMON_098", HttpStatus.INTERNAL_SERVER_ERROR, "데이터 처리 중 오류가 발생했습니다.")
    , INTERNAL_SERVER_ERROR("COMMON_099", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;
}
