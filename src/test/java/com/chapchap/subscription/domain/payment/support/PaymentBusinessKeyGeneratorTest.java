package com.chapchap.subscription.domain.payment.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentBusinessKeyGeneratorTest {
    @Test
    void generatesEveryDocumentedBusinessKeyFormat() {
        assertEquals("PAYMENT:FIRST:1", PaymentBusinessKeyGenerator.firstPayment(1L));
        assertEquals("PAYMENT:REGULAR:2", PaymentBusinessKeyGenerator.regularPayment(2L));
        assertEquals("PAYMENT:CHANGE:3", PaymentBusinessKeyGenerator.settingChange(3L));
        assertEquals("CANCEL:4:5", PaymentBusinessKeyGenerator.cancellation(4L, 5L));
    }

    @Test
    void nonPositiveIdentifierIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PaymentBusinessKeyGenerator.firstPayment(0L));
    }
}
