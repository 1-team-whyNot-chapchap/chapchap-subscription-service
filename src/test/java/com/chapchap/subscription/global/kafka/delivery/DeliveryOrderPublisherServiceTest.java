package com.chapchap.subscription.global.kafka.delivery;

import com.chapchap.subscription.domain.holiday.repository.HolidayRepository;
import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.entity.OrderKafkaDeliveryStatus;
import com.chapchap.subscription.domain.order.repository.KafkaDeliveryFailureRepository;
import com.chapchap.subscription.domain.order.repository.OrderDeliveryAttemptRepository;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.Menu;
import com.chapchap.subscription.domain.subscription.repository.MenuRepository;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.domain.terms.entity.UserTermsAgreement;
import com.chapchap.subscription.domain.terms.repository.UserTermsAgreementRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryOrderPublisherServiceTest {
    @Test
    void 익일_유효_주문을_공개_주문_ID_Key와_확정_Payload로_발행하고_완료로_기록한다() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderDeliveryAttemptRepository attempts = mock(OrderDeliveryAttemptRepository.class);
        HolidayRepository holidays = mock(HolidayRepository.class);
        MenuRepository menus = mock(MenuRepository.class);
        UserTermsAgreementRepository agreements = mock(UserTermsAgreementRepository.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        DeliveryOrderKafkaProperties properties = new DeliveryOrderKafkaProperties();
        properties.setTopic("msa4-team1.subscription.delivery-orders.v1");
        KafkaDeliveryFailureRepository failures = mock(KafkaDeliveryFailureRepository.class);
        DeliveryOrderPublisherService service = new DeliveryOrderPublisherService(
            orders, attempts, failures, holidays, menus, agreements, template, properties, new KstReferenceTimeProvider()
        );
        Order order = activeOrder();
        Menu menu = mock(Menu.class);
        when(menu.getPublicId()).thenReturn("00000000-0000-4000-8000-000000000001");
        UserTermsAgreement agreement = UserTermsAgreement.create(10L, 3L, LocalDateTime.of(2026, 9, 1, 10, 0));
        SendResult<String, Object> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.topic()).thenReturn("msa4-team1.subscription.delivery-orders.v1");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(42L);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(holidays.existsByHolidayDateIn(any())).thenReturn(false);
        when(orders.findAllByDeliveryDateAndStatusAndKafkaDeliveryStatus(any(), any(), any())).thenReturn(List.of(order));
        when(menus.findById(7L)).thenReturn(Optional.of(menu));
        when(agreements.findById(5L)).thenReturn(Optional.of(agreement));
        when(template.send(eq("msa4-team1.subscription.delivery-orders.v1"), eq(order.getPublicId()), any()))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        service.publishInitialOrders(LocalDate.of(2026, 9, 7));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(template).send(eq("msa4-team1.subscription.delivery-orders.v1"), eq(order.getPublicId()), eventCaptor.capture());
        SubscriptionDeliveryOrderReadyEvent event = (SubscriptionDeliveryOrderReadyEvent) eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(SubscriptionDeliveryOrderReadyEvent.EVENT_TYPE);
        assertThat(event.userId()).isEqualTo(10L);
        assertThat(event.data().postalCode()).isEqualTo("41911");
        assertThat(event.data().entranceInformation()).isEqualTo("7003");
        assertThat(event.data().menuItems()).singleElement().satisfies(item -> {
            assertThat(item.menuId()).isEqualTo(menu.getPublicId());
            assertThat(item.quantity()).isEqualTo(2);
        });
        assertThat(order.getKafkaDeliveryStatus()).isEqualTo(OrderKafkaDeliveryStatus.COMPLETED);
        verify(attempts).save(any());
    }

    private Order activeOrder() {
        Order order = Order.createAwaitingConfirmation(
            10L, 1L, 2L, 3L, 5L, 6L, 4L, 7L, LocalDate.of(2026, 9, 8), 1, "플랜", "메뉴",
            8_900L, 2, 17_800L, 0L, 0L, 17_800L, "홍길동", "010-0000-0000", "41911",
            "대구광역시 중구 국채보상로 1", "101동 1001호", "DOORSTEP", null, "7003", OrderDeliveryTimeSlot.TIME_1100_1300
        );
        ReflectionTestUtils.setField(order, "id", 100L);
        order.activateAfterPayment();
        return order;
    }
}
