package com.chapchap.subscription.global.exception.terms;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class TermsVersionMismatchException
        extends BusinessException {

    public TermsVersionMismatchException() {
        super(ErrorCode.TERMS_VERSION_MISMATCH);
    }
}
