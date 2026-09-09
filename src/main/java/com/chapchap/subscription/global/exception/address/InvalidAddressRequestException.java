package com.chapchap.subscription.global.exception.address;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class InvalidAddressRequestException extends BusinessException {

    public InvalidAddressRequestException() {
        super(ErrorCode.INVALID_REQUEST);
    }
}
