package com.chapchap.subscription.global.exception.payment;

import com.chapchap.subscription.global.exception.BusinessException;
import com.chapchap.subscription.global.exception.ErrorCode;

public class SettingChangePaymentDeclinedException extends BusinessException {
    public SettingChangePaymentDeclinedException() { super(ErrorCode.SETTING_CHANGE_PAYMENT_DECLINED); }
}
