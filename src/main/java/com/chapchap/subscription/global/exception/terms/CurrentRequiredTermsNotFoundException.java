package com.chapchap.subscription.global.exception.terms;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class CurrentRequiredTermsNotFoundException
        extends BusinessException {

    public CurrentRequiredTermsNotFoundException() {
        super(ErrorCode.CURRENT_REQUIRED_TERMS_NOT_FOUND);
    }
}
