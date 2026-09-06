package com.chapchap.subscription.domain.address.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.address.repository.DeliveryMethodRepository;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.global.exception.address.AddressInUseException;
import com.chapchap.subscription.global.kafka.customer.CustomerDeliveryAddressPublisher;
import com.chapchap.subscription.global.validation.PublicIdFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {
    @Mock private AddressRepository addressRepository;
    @Mock private DeliveryMethodRepository deliveryMethodRepository;
    @Mock private SubscriptionDeliveryConditionRepository conditionRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private KstReferenceTimeProvider timeProvider;

    private AddressService service;
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 6, 10, 0);

    @BeforeEach
    void setUp() {
        service = new AddressService(addressRepository, deliveryMethodRepository, conditionRepository, orderRepository, timeProvider, mock(CustomerDeliveryAddressPublisher.class));
        when(timeProvider.now()).thenReturn(now);
    }

    @Test
    void 현재_구독_배송_조건에서_사용하는_배송지는_삭제할_수_없다() {
        Address address = address();
        find(address);
        when(conditionRepository.existsCurrentConditionByAddressId(1L, SubscriptionSettingStatus.ACTIVE, now.toLocalDate())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteAddress(10L, address.getPublicId()))
            .isInstanceOf(AddressInUseException.class);
        assertThat(address.getDeletedAt()).isNull();
    }

    @Test
    void 오늘_이후_유효_주문에서_사용하는_배송지는_삭제할_수_없다() {
        Address address = address();
        find(address);
        when(conditionRepository.existsCurrentConditionByAddressId(1L, SubscriptionSettingStatus.ACTIVE, now.toLocalDate())).thenReturn(false);
        when(orderRepository.existsByAddressIdAndStatusAndDeliveryDateGreaterThanEqual(1L, OrderStatus.ACTIVE, now.toLocalDate())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteAddress(10L, address.getPublicId()))
            .isInstanceOf(AddressInUseException.class);
    }

    @Test
    void 사용하지_않는_일반_배송지는_소프트_삭제한다() {
        Address address = address();
        assertThat(PublicIdFormat.isUuidV4(address.getPublicId())).isTrue();
        find(address);
        when(conditionRepository.existsCurrentConditionByAddressId(1L, SubscriptionSettingStatus.ACTIVE, now.toLocalDate())).thenReturn(false);
        when(orderRepository.existsByAddressIdAndStatusAndDeliveryDateGreaterThanEqual(1L, OrderStatus.ACTIVE, now.toLocalDate())).thenReturn(false);

        service.deleteAddress(10L, address.getPublicId());

        assertThat(address.getDeletedAt()).isEqualTo(now);
    }

    private void find(Address address) {
        when(addressRepository.findByPublicIdAndUserIdAndDeletedAtIsNull(address.getPublicId(), 10L)).thenReturn(Optional.of(address));
    }

    private Address address() {
        Address address = Address.create(10L, "집", "홍길동", "01012345678", "12345", "대구광역시 중구", null, "DIRECT", null, null, false);
        ReflectionTestUtils.setField(address, "id", 1L);
        return address;
    }
}
