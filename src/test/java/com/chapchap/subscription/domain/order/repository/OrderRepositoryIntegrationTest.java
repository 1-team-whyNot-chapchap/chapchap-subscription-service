package com.chapchap.subscription.domain.order.repository;

import com.chapchap.subscription.domain.holiday.entity.Holiday;
import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderRepositoryIntegrationTest {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private HolidayRepository holidayRepository;

    @Test
    void 공식_공휴일과_대체공휴일_초기_데이터를_조회할_수_있다() {
        Holiday chuseok = holidayRepository.findById(LocalDate.of(2026, 9, 25)).orElseThrow();
        Holiday substituteHoliday = holidayRepository.findById(LocalDate.of(2026, 10, 5)).orElseThrow();

        assertThat(chuseok.getHolidayName()).isEqualTo("추석");
        assertThat(chuseok.isSubstituteHoliday()).isFalse();
        assertThat(substituteHoliday.getHolidayName()).contains("대체공휴일");
        assertThat(substituteHoliday.isSubstituteHoliday()).isTrue();
    }

    @Test
    void ACTIVE_주문의_조회용_생성_컬럼이_MySQL에서_계산된다() {
        long subscriptionId = uniquePositiveId();
        LocalDate deliveryDate = LocalDate.of(2026, 9, 7);
        Order order = order(subscriptionId, uniquePositiveId(), deliveryDate);
        orderRepository.saveAndFlush(order);

        order.activateAfterPayment();
        orderRepository.flush();
        entityManager.refresh(order);

        assertThat(order.getActiveSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(order.getActiveDeliveryDate()).isEqualTo(deliveryDate);
    }

    @Test
    void 같은_구독과_배송일의_동일_리비전_주문은_MySQL_UNIQUE_제약으로_거절된다() {
        long subscriptionId = uniquePositiveId();
        LocalDate deliveryDate = LocalDate.of(2026, 9, 7);
        orderRepository.saveAndFlush(order(subscriptionId, uniquePositiveId(), deliveryDate));

        assertThatThrownBy(() -> orderRepository.saveAndFlush(
            order(subscriptionId, uniquePositiveId(), deliveryDate)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void Kafka_전송_완료인데_저장시각이_없으면_영속화_직전에_거절된다() {
        Order order = order(uniquePositiveId(), uniquePositiveId(), LocalDate.of(2026, 9, 7));
        orderRepository.saveAndFlush(order);
        ReflectionTestUtils.setField(order, "kafkaDeliveryStatus", OrderKafkaDeliveryStatus.COMPLETED);

        assertThatThrownBy(() -> orderRepository.flush())
            .isInstanceOf(InvalidDataAccessApiUsageException.class)
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasMessageContaining("kafkaStoredAt");
    }

    private Order order(long subscriptionId, long subscriptionPeriodId, LocalDate deliveryDate) {
        return Order.awaitingConfirmationBuilder()
            .userId(uniquePositiveId())
            .subscriptionId(subscriptionId)
            .subscriptionPeriodId(subscriptionPeriodId)
            .subscriptionSettingId(uniquePositiveId())
            .termsAgreementId(uniquePositiveId())
            .planId(uniquePositiveId())
            .addressId(uniquePositiveId())
            .menuId(uniquePositiveId())
            .deliveryDate(deliveryDate)
            .revisionSequence(1)
            .planName("통합 테스트 플랜")
            .menuName("통합 테스트 메뉴")
            .mealUnitPrice(8_900L)
            .mealQuantity(2)
            .mealAmount(17_800L)
            .deliveryFee(3_000L)
            .discountAmount(2_670L)
            .actualAllocatedAmount(18_130L)
            .recipientName("테스트 수령인")
            .recipientPhone("010-0000-0000")
            .postalCode("00000")
            .addressLine1("테스트 주소")
            .deliveryMethodCode("DIRECT")
            .deliveryTimeSlot(OrderDeliveryTimeSlot.TIME_1100_1300)
            .build();
    }

    private long uniquePositiveId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }
}
