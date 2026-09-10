package com.chapchap.subscription.domain.terms.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 적용 중인 비대면 보관 약관")
public record TermsCurrentResponse(
        @Schema(description = "약관 제목", example = "비대면 보관 약관")
        String title,
        @Schema(description = "약관 본문", example = "고객은 비대면 보관 배송에 동의합니다.")
        String content,
        @Schema(description = "현재 약관 버전", example = "1", minimum = "1")
        Integer version
) {
}
