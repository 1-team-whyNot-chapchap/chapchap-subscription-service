package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Component
public class SubscriptionScheduleCalculator {

    private static final LocalTime REQUEST_CUTOFF_TIME = LocalTime.of(14, 0);
    private static final int PERIOD_DAYS = 28;
    private static final int MAX_FIRST_DELIVERY_SEARCH_DAYS = 366;

    public SubscriptionSchedule calculate(
            LocalDateTime calculationReferenceAt,
            Set<DeliveryWeekday> deliveryWeekdays,
            Set<LocalDate> holidays
    ) {
        validate(calculationReferenceAt, deliveryWeekdays, holidays);

        LocalDate reflectionDate = calculateReflectionDate(calculationReferenceAt);
        LocalDate periodStartDate = findFirstDeliveryDate(reflectionDate, deliveryWeekdays, holidays);
        LocalDate periodEndDate = periodStartDate.plusDays(PERIOD_DAYS - 1L);
        List<LocalDate> deliveryDates = IntStream.range(0, PERIOD_DAYS)
                .mapToObj(periodStartDate::plusDays)
                .filter(date -> isDeliveryDate(date, deliveryWeekdays, holidays))
                .toList();

        return new SubscriptionSchedule(
                reflectionDate,
                periodStartDate,
                periodEndDate,
                deliveryDates
        );
    }

    public int menuSequence(LocalDate deliveryDate) {
        if (deliveryDate == null) {
            throw new IllegalArgumentException("배송일은 필수입니다.");
        }
        return deliveryDate.getDayOfMonth();
    }

    LocalDate calculateReflectionDate(LocalDateTime calculationReferenceAt) {
        LocalDate reflectionDate = calculationReferenceAt.toLocalTime().isBefore(REQUEST_CUTOFF_TIME)
                ? calculationReferenceAt.toLocalDate().plusDays(1)
                : calculationReferenceAt.toLocalDate().plusDays(2);

        return reflectionDate.getDayOfWeek() == DayOfWeek.SUNDAY
                ? reflectionDate.plusDays(1)
                : reflectionDate;
    }

    private LocalDate findFirstDeliveryDate(
            LocalDate reflectionDate,
            Set<DeliveryWeekday> deliveryWeekdays,
            Set<LocalDate> holidays
    ) {
        for (int offset = 0; offset <= MAX_FIRST_DELIVERY_SEARCH_DAYS; offset++) {
            LocalDate candidate = reflectionDate.plusDays(offset);
            if (isDeliveryDate(candidate, deliveryWeekdays, holidays)) {
                return candidate;
            }
        }
        throw new IllegalStateException("반영 기준일 이후 1년 안에 실제 배송일을 찾을 수 없습니다.");
    }

    private boolean isDeliveryDate(
            LocalDate date,
            Set<DeliveryWeekday> deliveryWeekdays,
            Set<LocalDate> holidays
    ) {
        return deliveryWeekdays.stream()
                .anyMatch(weekday -> weekday.toDayOfWeek() == date.getDayOfWeek())
                && !holidays.contains(date);
    }

    private void validate(
            LocalDateTime calculationReferenceAt,
            Set<DeliveryWeekday> deliveryWeekdays,
            Set<LocalDate> holidays
    ) {
        if (calculationReferenceAt == null || holidays == null) {
            throw new IllegalArgumentException("계산 기준 시각과 공휴일 목록은 필수입니다.");
        }
        if (deliveryWeekdays == null || deliveryWeekdays.isEmpty() || deliveryWeekdays.size() > 6) {
            throw new IllegalArgumentException("배송 요일은 월요일부터 토요일 중 1개부터 6개까지 선택해야 합니다.");
        }
    }
}
