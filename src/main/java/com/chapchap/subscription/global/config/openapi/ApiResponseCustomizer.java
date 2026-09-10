package com.chapchap.subscription.global.config.openapi;

import com.chapchap.subscription.global.exception.ErrorCode;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/** {@link CustomApiResponse} 선언을 실제 GlobalResponse 오류 예시로 변환한다. */
@Component
public class ApiResponseCustomizer implements OperationCustomizer {

    @Override
    public io.swagger.v3.oas.models.Operation customize(
        io.swagger.v3.oas.models.Operation operation,
        HandlerMethod handlerMethod
    ) {
        CustomApiResponse customApiResponse = handlerMethod.getMethodAnnotation(CustomApiResponse.class);
        if (customApiResponse == null) {
            return operation;
        }
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }

        Map<Integer, List<ErrorCode>> errorsByStatus = Arrays.stream(customApiResponse.value())
            .collect(Collectors.groupingBy(
                errorCode -> errorCode.getHttpStatus().value(),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        errorsByStatus.forEach((status, errorCodes) -> {
            MediaType mediaType = new MediaType();
            errorCodes.forEach(errorCode -> mediaType.addExamples(
                errorCode.getCode(),
                new Example().value(errorExample(errorCode))
            ));

            operation.getResponses().addApiResponse(
                String.valueOf(status),
                new ApiResponse()
                    .description(errorCodes.stream()
                        .map(ErrorCode::getMessage)
                        .collect(Collectors.joining(" / ")))
                    .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        mediaType
                    ))
            );
        });

        return operation;
    }

    private Map<String, Object> errorExample(ErrorCode errorCode) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", errorCode.getCode());
        response.put("message", errorCode.getMessage());
        response.put("data", null);
        return response;
    }
}
