package com.chapchap.subscription.domain.holiday.service;

import com.chapchap.subscription.domain.holiday.entity.Holiday;
import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.holiday.response.HolidayListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 공개 메뉴 안내용 공휴일을 조회하며 주문·결제의 날짜 계산에는 관여하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HolidayQueryService {
    // 다음 연도 추가 시 초기 데이터와 실제 DB를 검증한 뒤 함께 확장한다.
    private static final LocalDate SUPPORTED_START_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate SUPPORTED_END_DATE = LocalDate.of(2027, 12, 31);

    private final HolidayRepository holidayRepository;

    public HolidayListResponse getHolidays() {
        List<Holiday> holidays = holidayRepository.findAllByHolidayDateBetween(
            SUPPORTED_START_DATE, SUPPORTED_END_DATE
        );
        validateHolidays(holidays);
        return new HolidayListResponse(
            SUPPORTED_START_DATE,
            SUPPORTED_END_DATE,
            holidays.stream()
                .sorted(Comparator.comparing(Holiday::getHolidayDate))
                .map(holiday -> new HolidayListResponse.HolidayItemResponse(
                    holiday.getHolidayDate(), holiday.getHolidayName(), holiday.isSubstituteHoliday()
                ))
                .toList()
        );
    }

    private void validateHolidays(List<Holiday> holidays) {
        Set<LocalDate> dates = new HashSet<>();
        Set<Integer> years = new HashSet<>();
        for (Holiday holiday : holidays) {
            if (holiday == null || holiday.getHolidayDate() == null
                || holiday.getHolidayName() == null || holiday.getHolidayName().isBlank()
                || holiday.getHolidayName().length() > 100) {
                throw inconsistentHolidayData();
            }
            LocalDate date = holiday.getHolidayDate();
            if (date.isBefore(SUPPORTED_START_DATE) || date.isAfter(SUPPORTED_END_DATE)
                || !dates.add(date)) {
                throw inconsistentHolidayData();
            }
            years.add(date.getYear());
        }
        // 빈 목록·연도 전체 미준비만 탐지한다. 일부 공휴일 누락은 별도 데이터 검증이 필요하다.
        for (int year = SUPPORTED_START_DATE.getYear(); year <= SUPPORTED_END_DATE.getYear(); year++) {
            if (!years.contains(year)) {
                throw inconsistentHolidayData();
            }
        }
    }

    private IllegalStateException inconsistentHolidayData() {
        return new IllegalStateException("공휴일 정보 제공 범위의 기준 데이터가 준비되지 않았거나 올바르지 않습니다.");
    }
}
