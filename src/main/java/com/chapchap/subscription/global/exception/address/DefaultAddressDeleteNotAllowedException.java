package com.chapchap.subscription.global.exception.address;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class DefaultAddressDeleteNotAllowedException
        extends BusinessException {

    public DefaultAddressDeleteNotAllowedException() {
        super(ErrorCode.DEFAULT_ADDRESS_DELETE_NOT_ALLOWED);
    }
}
