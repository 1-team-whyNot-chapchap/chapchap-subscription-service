package com.chapchap.subscription.global.exception.terms;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class TermsAgreementRequiredException extends BusinessException {

    public TermsAgreementRequiredException() {
        super(ErrorCode.TERMS_AGREEMENT_REQUIRED);
    }
}
