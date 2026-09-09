package com.chapchap.subscription.global.response;

import com.chapchap.subscription.global.exception.ErrorCode;

public record GlobalResponse<T>(
    String code
    , String message
    , T data
) {

    private static final String SUCCESS_CODE = "00";
    private static final String SUCCESS_MESSAGE = "SUCCESS";

    // 데이터가 있는 성공 응답
    public static <T> GlobalResponse<T> success(T data) {
        return of(
            SUCCESS_CODE
            , SUCCESS_MESSAGE
            , data
        );
    }

    // 데이터가 없는 성공 응답
    public static GlobalResponse<Void> success() {
        return of(
            SUCCESS_CODE
            , SUCCESS_MESSAGE
            , null
        );
    }

    // 오류 코드로 실패 응답
    public static GlobalResponse<Void> from(ErrorCode errorCode) {
        return of(
            errorCode.getCode()
            , errorCode.getMessage()
            , null
        );
    }

    // 모든 응답 객체 생성 담당
    private static <T> GlobalResponse<T> of(
        String code
        , String message
        , T data
    ) {
        return new GlobalResponse<>(
            code
            , message
            , data
        );
    }
}