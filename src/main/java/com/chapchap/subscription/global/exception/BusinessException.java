package com.chapchap.subscription.global.exception;

import lombok.Getter;

/** 외부 API 오류로 변환할 수 있는 업무 예외의 공통 기반 클래스다. */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    /** 구체적인 업무 예외가 반환할 오류 코드와 기본 메시지를 보존한다. */
    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
