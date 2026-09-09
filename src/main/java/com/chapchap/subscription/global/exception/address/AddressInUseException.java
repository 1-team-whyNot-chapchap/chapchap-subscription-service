package com.chapchap.subscription.global.exception.address;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class AddressInUseException extends BusinessException {

    public AddressInUseException() {
        super(ErrorCode.ADDRESS_IN_USE);
    }
}