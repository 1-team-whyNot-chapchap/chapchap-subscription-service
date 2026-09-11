package com.chapchap.subscription.domain.subscription.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 공개 플랜과 1~31번 고정 메뉴 안내 응답이다. */
@Schema(description = "공개 구독 플랜 상세와 고정 메뉴 안내")
public record PlanDetailResponse(
    @Schema(description = "플랜의 공개 UUID", format = "uuid", example = "e68fd1c3-bcad-4b19-a417-c7c067a6062a")
    String planId,
    @Schema(description = "플랜명", example = "간편식")
    String name,
    @Schema(description = "플랜 설명", example = "간편하게 즐기는 한 끼 도시락")
    String description,
    @Schema(description = "1인 1식 기준 단가(원)", example = "10900")
    Long unitPrice,
    @Schema(description = "메뉴 순번 오름차순의 고정 메뉴 안내 목록")
    List<MenuResponse> menus
) {
    public PlanDetailResponse {
        menus = List.copyOf(menus);
    }

    @Schema(description = "고정 메뉴 안내 항목")
    public record MenuResponse(
        @Schema(description = "플랜 내 메뉴 순번(1~31)", example = "1")
        Integer menuSequence,
        @Schema(description = "메뉴명", example = "닭가슴살 샐러드")
        String name,
        @Schema(description = "메뉴 설명", example = "담백한 닭가슴살과 신선한 채소")
        String description,
        @Schema(description = "고객 표시용 메뉴 이미지 URL", nullable = true, example = "https://cdn.example.com/menus/meal-001.jpg")
        String imageUrl,
        @Schema(description = "알레르기 유발 정보", nullable = true, example = "대두, 우유")
        String allergenInfo,
        @Schema(description = "영양 정보", nullable = true, example = "열량 450kcal")
        String nutritionInfo,
        @Schema(description = "원재료 정보", nullable = true, example = "닭가슴살, 현미, 채소")
        String ingredientInfo
    ) {
    }
}
