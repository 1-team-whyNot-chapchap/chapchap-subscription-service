package com.chapchap.subscription.domain.payment.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentAllocationTest {
    @Test
    void allocationStartsWithZeroCancelledAmount() {
        PaymentAllocation allocation = PaymentAllocation.create(
            10L,
            20L,
            PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT,
            30_000L
        );

        assertEquals(10L, allocation.getOrderId());
        assertEquals(20L, allocation.getOriginalPaymentTransactionId());
        assertEquals(PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT, allocation.getAllocationType());
        assertEquals(30_000L, allocation.getAllocatedAmount());
        assertEquals(0L, allocation.getCumulativeCancelledAmount());
    }

    @Test
    void zeroAllocationAmountIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PaymentAllocation.create(
                10L,
                20L,
                PaymentAllocationType.FIRST_SUBSCRIPTION_PAYMENT,
                0L
            )
        );
    }
}
