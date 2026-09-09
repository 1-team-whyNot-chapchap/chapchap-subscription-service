package com.chapchap.subscription.domain.address.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;

// set...() = "요청에 이 필드가 들어왔다" + 값 저장, 값을 받는 쪽
// has...() = "이 필드가 요청에 들어왔었나?" 확인, 그 값을 "보냈는지" 확인하는 쪽
@Getter
public class AddressUpdateRequest {

    @Size(max = 50)
    private String name;
    private boolean namePresent;

    @Size(max = 50)
    private String recipientName;
    private boolean recipientNamePresent;

    @Size(max = 20)
    private String recipientPhone;
    private boolean recipientPhonePresent;

    @Size(max = 10)
    private String postalCode;
    private boolean postalCodePresent;

    @Size(max = 255)
    private String addressLine1;
    private boolean addressLine1Present;

    @Size(max = 255)
    private String addressLine2;
    private boolean addressLine2Present;

    @Size(max = 20)
    private String deliveryMethod;
    private boolean deliveryMethodPresent;

    @Size(max = 255)
    private String otherDeliveryRequest;
    private boolean otherDeliveryRequestPresent;

    @Size(max = 100)
    private String entrancePassword;
    private boolean entrancePasswordPresent;

    public void setName(String name) {
        this.namePresent = true;
        this.name = name;
    }

    public void setRecipientName(String recipientName) {
        this.recipientNamePresent = true;
        this.recipientName = recipientName;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhonePresent = true;
        this.recipientPhone = recipientPhone;
    }

    public void setPostalCode(String postalCode) {
        this.postalCodePresent = true;
        this.postalCode = postalCode;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1Present = true;
        this.addressLine1 = addressLine1;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2Present = true;
        this.addressLine2 = addressLine2;
    }

    public void setDeliveryMethod(String deliveryMethod) {
        this.deliveryMethodPresent = true;
        this.deliveryMethod = deliveryMethod;
    }

    public void setOtherDeliveryRequest(String otherDeliveryRequest) {
        this.otherDeliveryRequestPresent = true;
        this.otherDeliveryRequest = otherDeliveryRequest;
    }

    public void setEntrancePassword(String entrancePassword) {
        this.entrancePasswordPresent = true;
        this.entrancePassword = entrancePassword;
    }

    public boolean hasName() {
        return namePresent;
    }

    public boolean hasRecipientName() {
        return recipientNamePresent;
    }

    public boolean hasRecipientPhone() {
        return recipientPhonePresent;
    }

    public boolean hasPostalCode() {
        return postalCodePresent;
    }

    public boolean hasAddressLine1() {
        return addressLine1Present;
    }

    public boolean hasAddressLine2() {
        return addressLine2Present;
    }

    public boolean hasDeliveryMethod() {
        return deliveryMethodPresent;
    }

    public boolean hasOtherDeliveryRequest() {
        return otherDeliveryRequestPresent;
    }

    public boolean hasEntrancePassword() {
        return entrancePasswordPresent;
    }
}
