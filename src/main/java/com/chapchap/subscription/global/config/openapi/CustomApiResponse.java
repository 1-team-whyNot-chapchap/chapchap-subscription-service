package com.chapchap.subscription.global.config.openapi;

import com.chapchap.subscription.global.exception.ErrorCode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 컨트롤러 작업에서 반환 가능한 업무 오류를 OpenAPI 문서에 선언한다. */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomApiResponse {

    ErrorCode[] value();
}
