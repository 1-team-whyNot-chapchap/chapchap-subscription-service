package com.chapchap.subscription.domain.address.service;

import com.chapchap.subscription.domain.address.entity.Address;
import com.chapchap.subscription.domain.address.repository.AddressRepository;
import com.chapchap.subscription.domain.address.repository.DeliveryMethodRepository;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.order.repository.OrderRepository;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.service.KstReferenceTimeProvider;
import com.chapchap.subscription.domain.address.request.AddressCreateRequest;
import com.chapchap.subscription.domain.address.request.AddressUpdateRequest;
import com.chapchap.subscription.domain.address.response.*;
import com.chapchap.subscription.global.exception.address.AddressNotFoundException;
import com.chapchap.subscription.global.exception.address.AddressOutOfServiceAreaException;
import com.chapchap.subscription.global.exception.address.AddressInUseException;
import com.chapchap.subscription.global.exception.address.DefaultAddressDeleteNotAllowedException;
import com.chapchap.subscription.global.exception.address.InvalidAddressRequestException;
import com.chapchap.subscription.global.kafka.customer.CustomerDeliveryAddressPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    private static final String SERVICE_AREA_KEYWORD = "대구";
    private static final String DELIVERY_METHOD_OTHER = "OTHER";

    private final AddressRepository addressRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;
    private final SubscriptionDeliveryConditionRepository subscriptionDeliveryConditionRepository;
    private final OrderRepository orderRepository;
    private final KstReferenceTimeProvider timeProvider;
    private final CustomerDeliveryAddressPublisher customerDeliveryAddressPublisher;

    // 로그인한 사용자의 삭제되지 않은 배송지 목록을 조회하고 Response DTO로 변환한다.
    public AddressListResponse getAddresses(Long userId) {
        List<AddressItemResponse> addresses =
                addressRepository
                        .findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(userId)
                        .stream()
                        .map(this::toItemResponse)
                        .toList();

        return new AddressListResponse(addresses);
    }

    /** 인증 고객 소유이며 삭제되지 않은 첫 구독 배송지를 조회하고, 없으면 ADDRESS_001을 발생시킨다. */
    public Address requireActiveAddress(Long userId, String addressId) {
        return addressRepository
                .findByPublicIdAndUserIdAndDeletedAtIsNull(addressId, userId)
                .orElseThrow(AddressNotFoundException::new);
    }

    // 새 배송지를 등록한다.
        // 필수값·대구 배송 가능 여부·배달 방식 조건을 검증하고,
        // 첫 배송지면 자동으로 기본 배송지로 만든다.
    @Transactional
    public AddressCreateResponse createAddress(
            Long userId,
            AddressCreateRequest request
    ) {
        validateRequiredValue(request.name());
        validateRequiredValue(request.recipientName());
        validateRequiredValue(request.recipientPhone());
        validateAddress(
                request.postalCode(),
                request.addressLine1()
        );
        validateDeliveryMethod(
                request.deliveryMethod(),
                request.otherDeliveryRequest()
        );

        boolean isFirstAddress =
                !addressRepository.existsByUserIdAndDeletedAtIsNull(userId);

        Address address = Address.create(
                userId,
                request.name(),
                request.recipientName(),
                request.recipientPhone(),
                request.postalCode(),
                request.addressLine1(),
                request.addressLine2(),
                request.deliveryMethod(),
                request.otherDeliveryRequest(),
                request.entrancePassword(),
                isFirstAddress
        );

        // DeliveryAddressVersion 버전 번호 업데이트
        // 배송지 등록은 Customer 배송지 변경 사실이므로 최초 순번 0 → 1
        address.increaseDeliveryAddressVersion();

        Address savedAddress = addressRepository.save(address);
        customerDeliveryAddressPublisher.publishChangedAfterCommit(savedAddress, timeProvider.now());

        return new AddressCreateResponse(
                savedAddress.getPublicId(),
                savedAddress.isDefault()
        );
    }

    // 기존 배송지를 부분 수정(PATCH) 한다.
        // 요청에 없는 값은 유지하고, nullable 필드에 null이 명시되면 기존 값을 제거한다.
        // 수정 후 최종 주소와 배달 방식 조건도 다시 검증한다.
    @Transactional
    public AddressUpdateResponse updateAddress(
            Long userId,
            String addressId,
            AddressUpdateRequest request
    ) {

        // .orElseThrow(AddressNotFoundException::new) : null이면 에러 발생
        Address address = addressRepository
                .findByPublicIdAndUserIdAndDeletedAtIsNull(addressId, userId)
                .orElseThrow(AddressNotFoundException::new);

        // null이나 빈 값이 아니면 값 저장
        String name = request.hasName()
                ? requireRequiredValue(request.getName())
                : address.getName();

        String recipientName = request.hasRecipientName()
                ? requireRequiredValue(request.getRecipientName())
                : address.getRecipientName();

        String recipientPhone = request.hasRecipientPhone()
                ? requireRequiredValue(request.getRecipientPhone())
                : address.getRecipientPhone();

        String postalCode = request.hasPostalCode()
                ? requireRequiredValue(request.getPostalCode())
                : address.getPostalCode();

        String addressLine1 = request.hasAddressLine1()
                ? requireRequiredValue(request.getAddressLine1())
                : address.getAddressLine1();

        String addressLine2 = request.hasAddressLine2()
                ? request.getAddressLine2()
                : address.getAddressLine2();

        String deliveryMethod = request.hasDeliveryMethod()
                ? requireRequiredValue(request.getDeliveryMethod())
                : address.getDeliveryMethodCode();

        String otherDeliveryRequest = request.hasOtherDeliveryRequest()
                ? request.getOtherDeliveryRequest()
                : address.getOtherDeliveryRequest();

        String entrancePassword = request.hasEntrancePassword()
                ? request.getEntrancePassword()
                : address.getEntrancePassword();

        validateAddress(postalCode, addressLine1);
        validateDeliveryMethod(
                deliveryMethod,
                otherDeliveryRequest
        );

        // req 값과 DB의 값이 일치 여부
        boolean customerVisibleChanged =
                hasVisibleAddressChanged(
                        address,
                        name,
                        recipientName,
                        recipientPhone,
                        postalCode,
                        addressLine1,
                        addressLine2,
                        deliveryMethod,
                        otherDeliveryRequest
                );

        // req으로 온 값을 DB에 저장
        address.changeDetails(
                name,
                recipientName,
                recipientPhone,
                postalCode,
                addressLine1,
                addressLine2,
                deliveryMethod,
                otherDeliveryRequest,
                entrancePassword
        );

        // 데이터가 변경되었을 경우 DeliveryAddressVersio 버전 업데이트
        if (customerVisibleChanged) {
            address.increaseDeliveryAddressVersion();
            customerDeliveryAddressPublisher.publishChangedAfterCommit(address, timeProvider.now());
        }

        return new AddressUpdateResponse(address.getPublicId());
    }

    // Address Entity 한 개를 API 응답용 AddressItemResponse로 변환한다.(최종적으로 배송지들을 리스트로 묶어 반환할 것이므로)
    private AddressItemResponse toItemResponse(Address address) {
        return new AddressItemResponse(
                address.getPublicId(),
                address.getName(),
                address.getRecipientName(),
                address.getRecipientPhone(),
                address.getPostalCode(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getDeliveryMethodCode(),
                address.getOtherDeliveryRequest(),
                address.isDefault()
        );
    }

    // 우편번호·기본 주소가 있는지 확인하고, 기본 주소에 대구가 포함되는지 검사한다.
    private void validateAddress(
            String postalCode,
            String addressLine1
    ) {
        validateRequiredValue(postalCode);
        validateRequiredValue(addressLine1);

        // 기본주소에 '대구' 포함 안 되면 에러 발생
        if (!addressLine1.contains(SERVICE_AREA_KEYWORD)) {
            throw new AddressOutOfServiceAreaException();
        }
    }

    // DIRECT, DOORSTEP, OTHER 같은 배달 방식이 실제 등록된 코드인지 검사하고, OTHER일 때만 추가 요청이 있도록 검증한다.
        // 유효하지 않는 값이면 에러
    private void validateDeliveryMethod(
            String deliveryMethod,
            String otherDeliveryRequest
    ) {
        validateRequiredValue(deliveryMethod);

        if (!deliveryMethodRepository.existsById(deliveryMethod)) {
            throw new InvalidAddressRequestException();
        }

        // 배송 방법이 OTHER일 때만 추가 요청 작성 가능
        if (DELIVERY_METHOD_OTHER.equals(deliveryMethod)) {
            if (otherDeliveryRequest == null
                    || otherDeliveryRequest.isBlank()) {
                throw new InvalidAddressRequestException();
            }

            return;
        }

        if (otherDeliveryRequest != null) {
            throw new InvalidAddressRequestException();
        }
    }

    // 필수 문자열이 null, 빈 문자열, 공백 문자열인지 검사한다.
    private void validateRequiredValue(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidAddressRequestException();
        }
    }

    // PATCH에서 필수인 필드가 null이나 공백이면 실패시키고, 정상 값이면 그대로 반환한다.
    private String requireRequiredValue(String value) {
        validateRequiredValue(value);
        return value;
    }

    // req 값과 DB의 값이 일치하는지 확인
    private boolean hasVisibleAddressChanged(
            Address address,
            String name,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String addressLine1,
            String addressLine2,
            String deliveryMethod,
            String otherDeliveryRequest
    ) {
        return !Objects.equals(address.getName(), name)
                || !Objects.equals(address.getRecipientName(), recipientName)
                || !Objects.equals(address.getRecipientPhone(), recipientPhone)
                || !Objects.equals(address.getPostalCode(), postalCode)
                || !Objects.equals(address.getAddressLine1(), addressLine1)
                || !Objects.equals(address.getAddressLine2(), addressLine2)
                || !Objects.equals(
                address.getDeliveryMethodCode(),
                deliveryMethod
        )
                || !Objects.equals(
                address.getOtherDeliveryRequest(),
                otherDeliveryRequest
        );
    }

    // -------------------------------------------------------------------------------------------

    // 기본 배송지 지정하기
    @Transactional
    public AddressDefaultResponse setDefaultAddress(
            Long userId,
            String addressId
    ) {
        // 기존의 기본 배송지 조회
        Address newDefaultAddress = addressRepository
                .findByPublicIdAndUserIdAndDeletedAtIsNull(
                        addressId,
                        userId
                )
                .orElseThrow(AddressNotFoundException::new);

        // 이미 기본 배송지라면 상태 변경 없이 성공
        if (newDefaultAddress.isDefault()) {
            return new AddressDefaultResponse(
                    newDefaultAddress.getPublicId(),
                    true
            );
        }

        // 기본 배송지가 따로 존재하면, 그친구의 기본 배송지 해제시키고 새로운 친구를 기본 배송지로 설정
        // DeliveryAddressVersion 버전 숫자 올림
        Address currentDefaultAddress = addressRepository
                .findByUserIdAndIsDefaultTrueAndDeletedAtIsNull(userId)
                .orElse(null);

        if (currentDefaultAddress != null) {
            currentDefaultAddress.unsetDefault();

            /*
             * active_default_user_id UNIQUE 제약 때문에
             * 기존 기본 배송지의 상태를 DB에 먼저 반영한다.
             */
            addressRepository.flush();
        }

        newDefaultAddress.setAsDefault();
        newDefaultAddress.increaseDeliveryAddressVersion();

        return new AddressDefaultResponse(
                newDefaultAddress.getPublicId(),
                true
        );
    }

    // ---------------------------------------------------------------------------------------------

    // 배송지 삭제
    @Transactional
    public AddressDeleteResponse deleteAddress(
            Long userId,
            String addressId
    ) {

        // 기존 배송지 조회
        Address address = addressRepository
                .findByPublicIdAndUserIdAndDeletedAtIsNull(
                        addressId,
                        userId
                )
                .orElseThrow(AddressNotFoundException::new);

        // 기본배송지인지 확인
        if (address.isDefault()) {
            customerDeliveryAddressPublisher.publishRejectedAfterCommit(address, "ADDRESS_003", timeProvider.now());
            throw new DefaultAddressDeleteNotAllowedException();
        }

        LocalDate todayKst = timeProvider.now().toLocalDate();
        if (subscriptionDeliveryConditionRepository.existsCurrentConditionByAddressId(
                address.getId(), SubscriptionSettingStatus.ACTIVE, todayKst
        ) || orderRepository.existsByAddressIdAndStatusAndDeliveryDateGreaterThanEqual(
                address.getId(), OrderStatus.ACTIVE, todayKst
        )) {
            customerDeliveryAddressPublisher.publishRejectedAfterCommit(address, "ADDRESS_004", timeProvider.now());
            throw new AddressInUseException();
        }

        // 문제 없으면 소프트딜리트 처리
        address.softDelete(
                timeProvider.now()
        );

        return new AddressDeleteResponse(
                address.getPublicId()
        );
    }
}
