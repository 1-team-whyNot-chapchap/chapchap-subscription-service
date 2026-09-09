package com.chapchap.subscription.global.exception;

import com.chapchap.subscription.global.response.GlobalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<GlobalResponse<Void>> generateErrorResponse(
            ErrorCode errorCode
    ) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(GlobalResponse.from(errorCode));
    }

    // --------------------------------------------------
    // === Subscription Service 기능 안에서의 예외 처리 ===
    // --------------------------------------------------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(
            BusinessException e
    ) {
        log.debug(
                "{}: {}"
                , e.getErrorCode().name()
                , e.getMessage()
        );
        return generateErrorResponse(e.getErrorCode());
    }

    // --------------------------------
    // === Spring 에서 발생하는 예외 ===
    // --------------------------------

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(
            AuthorizationDeniedException e
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken) {
            log.debug(ErrorCode.AUTHENTICATION_REQUIRED.name());
            return generateErrorResponse(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        throw e;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(
            MethodArgumentTypeMismatchException e
    ) {
        log.debug(
                "{}: invalid parameter {}"
                , ErrorCode.INVALID_REQUEST.name()
                , e.getName()
        );
        return generateErrorResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(
            MethodArgumentNotValidException e
    ) {
        Map<String, String> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError ->
                                fieldError.getDefaultMessage() != null
                                        ? fieldError.getDefaultMessage()
                                        : "유효하지 않은 값입니다.",
                        (existing, replacement) -> existing
                ));

        log.debug(
                "{}: {}"
                , ErrorCode.INVALID_REQUEST.name()
                , errors
        );
        return generateErrorResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(
            HttpMessageNotReadableException e
    ) {
        log.debug(ErrorCode.INVALID_REQUEST.name());
        return generateErrorResponse(ErrorCode.INVALID_REQUEST);
    }

    /** 도메인 입력값 검증에서 발생한 인자 오류를 공통 요청 검증 오류로 변환한다. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(
            IllegalArgumentException e
    ) {
        log.debug("{}: {}", ErrorCode.INVALID_REQUEST.name(), e.getMessage());
        return generateErrorResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<GlobalResponse<Void>> handle(
            DataAccessException e
    ) {
        log.error(ErrorCode.DATABASE_ERROR.name(), e);
        return generateErrorResponse(ErrorCode.DATABASE_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalResponse<Void>> handle(
            Exception e
    ) {
        log.error(ErrorCode.INTERNAL_SERVER_ERROR.name(), e);
        return generateErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
