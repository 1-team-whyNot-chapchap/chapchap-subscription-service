package com.chapchap.subscription.domain.subscription.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 공개 플랜 목록 응답이다. */
@Schema(description = "선택 가능한 공개 구독 플랜 목록")
public record PlanListResponse(
    @Schema(description = "단가 오름차순으로 정렬된 플랜 목록")
    List<PlanItemResponse> plans
) {
    public PlanListResponse {
        plans = List.copyOf(plans);
    }

    @Schema(description = "공개 구독 플랜 목록 항목")
    public record PlanItemResponse(
        @Schema(description = "플랜의 공개 UUID", format = "uuid", example = "e68fd1c3-bcad-4b19-a417-c7c067a6062a")
        String planId,
        @Schema(description = "플랜명", example = "간편식")
        String name,
        @Schema(description = "플랜 설명", example = "간편하게 즐기는 한 끼 도시락")
        String description,
        @Schema(description = "1인 1식 기준 단가(원)", example = "10900")
        Long unitPrice
    ) {
    }
}
