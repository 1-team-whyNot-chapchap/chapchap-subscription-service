package com.chapchap.subscription.domain.holiday.repository;

import com.chapchap.subscription.domain.holiday.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** 주문 생성 대상에 공휴일이 포함됐는지 조회한다. */
public interface HolidayRepository extends JpaRepository<Holiday, LocalDate> {
    /**
     * 대상 날짜 중 대한민국 공식 공휴일·대체공휴일이 하나라도 있는지 확인한다.
     *
     * @param holidayDates 확인할 실제 배송일 후보
     * @return 공휴일이 하나라도 포함되면 {@code true}
     */
    boolean existsByHolidayDateIn(Collection<LocalDate> holidayDates);

    /** 구독 기간 계산에 사용할 기준일 범위의 공휴일을 조회한다. */
    List<Holiday> findAllByHolidayDateBetween(LocalDate startDate, LocalDate endDate);
}
