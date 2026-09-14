package com.chapchap.subscription.domain.holiday.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** 정보 제공 범위와 공개 공휴일 목록이다. */
@Schema(description = "공휴일 정보 제공 범위와 범위 안의 공휴일 전체")
public record HolidayListResponse(
    @Schema(description = "공휴일 정보 제공 시작일(포함). 첫 공휴일 날짜가 아님", format = "date", example = "2026-01-01")
    LocalDate supportedStartDate,
    @Schema(description = "공휴일 정보 제공 종료일(포함). 범위 밖 날짜는 공휴일 여부를 보장하지 않음", format = "date", example = "2027-12-31")
    LocalDate supportedEndDate,
    @Schema(description = "날짜 오름차순으로 정렬된 공휴일 전체")
    List<HolidayItemResponse> holidays
) {
    public HolidayListResponse {
        holidays = List.copyOf(holidays);
    }

    @Schema(description = "공개 공휴일 기준정보")
    public record HolidayItemResponse(
        @Schema(description = "공휴일 날짜", format = "date", example = "2026-03-02")
        LocalDate holidayDate,
        @Schema(description = "공휴일 이름", maxLength = 100, example = "대체공휴일(3·1절)")
        String holidayName,
        @Schema(description = "대체공휴일 여부", example = "true")
        boolean substituteHoliday
    ) {
    }
}
