package com.chapchap.subscription.domain.address.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** 포함 여부를 별도 상태로 보관해 null을 통한 선택 필드 초기화를 지원한다. */
@Getter
@Schema(description = "배송지 부분 수정 요청. 포함하지 않은 필드는 변경하지 않으며, 선택 필드에 null을 보내면 기존 값을 지웁니다.")
public class AddressUpdateRequest {

    @Schema(description = "고객이 배송지를 구분하는 이름", example = "회사")
    @Size(max = 50) private String name;
    @Schema(hidden = true) private boolean namePresent;

    @Schema(description = "수령인 이름", example = "홍길동")
    @Size(max = 50) private String recipientName;
    @Schema(hidden = true) private boolean recipientNamePresent;

    @Schema(description = "수령인 연락처", example = "01012345678")
    @Size(max = 20) private String recipientPhone;
    @Schema(hidden = true) private boolean recipientPhonePresent;

    @Schema(description = "우편번호", example = "41911")
    @Size(max = 10) private String postalCode;
    @Schema(hidden = true) private boolean postalCodePresent;

    @Schema(description = "기본 주소", example = "대구광역시 중구 국채보상로 123")
    @Size(max = 255) private String addressLine1;
    @Schema(hidden = true) private boolean addressLine1Present;

    @Schema(description = "상세 주소", example = "101동 1001호")
    @Size(max = 255) private String addressLine2;
    @Schema(hidden = true) private boolean addressLine2Present;

    @Schema(description = "배달 방식 코드", allowableValues = {"DIRECT", "DOORSTEP", "OTHER"}, example = "DOORSTEP")
    @Size(max = 20) private String deliveryMethod;
    @Schema(hidden = true) private boolean deliveryMethodPresent;

    @Schema(description = "배달 방식이 OTHER일 때의 고객 직접 입력 요청", example = "경비실에 맡겨주세요")
    @Size(max = 255) private String otherDeliveryRequest;
    @Schema(hidden = true) private boolean otherDeliveryRequestPresent;

    @Schema(description = "필요한 경우에만 입력하는 공동현관 비밀번호", example = "1234")
    @Size(max = 100) private String entrancePassword;
    @Schema(hidden = true) private boolean entrancePasswordPresent;

    public void setName(String value) { namePresent = true; name = value; }
    public void setRecipientName(String value) { recipientNamePresent = true; recipientName = value; }
    public void setRecipientPhone(String value) { recipientPhonePresent = true; recipientPhone = value; }
    public void setPostalCode(String value) { postalCodePresent = true; postalCode = value; }
    public void setAddressLine1(String value) { addressLine1Present = true; addressLine1 = value; }
    public void setAddressLine2(String value) { addressLine2Present = true; addressLine2 = value; }
    public void setDeliveryMethod(String value) { deliveryMethodPresent = true; deliveryMethod = value; }
    public void setOtherDeliveryRequest(String value) { otherDeliveryRequestPresent = true; otherDeliveryRequest = value; }
    public void setEntrancePassword(String value) { entrancePasswordPresent = true; entrancePassword = value; }

    public boolean hasName() { return namePresent; }
    public boolean hasRecipientName() { return recipientNamePresent; }
    public boolean hasRecipientPhone() { return recipientPhonePresent; }
    public boolean hasPostalCode() { return postalCodePresent; }
    public boolean hasAddressLine1() { return addressLine1Present; }
    public boolean hasAddressLine2() { return addressLine2Present; }
    public boolean hasDeliveryMethod() { return deliveryMethodPresent; }
    public boolean hasOtherDeliveryRequest() { return otherDeliveryRequestPresent; }
    public boolean hasEntrancePassword() { return entrancePasswordPresent; }
}
