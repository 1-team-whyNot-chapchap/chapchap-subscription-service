package com.chapchap.subscription.global.exception.address;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class AddressOutOfServiceAreaException extends BusinessException {

    public AddressOutOfServiceAreaException() {
        super(ErrorCode.ADDRESS_OUT_OF_SERVICE_AREA);
    }
}
