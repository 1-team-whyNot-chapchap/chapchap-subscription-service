package com.chapchap.subscription.domain.order.response;

import com.chapchap.subscription.domain.order.entity.OrderDeliveryTimeSlot;
import com.chapchap.subscription.domain.order.entity.OrderStatus;
import com.chapchap.subscription.domain.payment.entity.RefundStatus;

import java.time.LocalDate;

/** 주문 당시 스냅샷과 고정 메뉴 안내를 함께 제공하는 주문 상세 응답이다. */
public record OrderDetailResponse(
    String orderId,
    LocalDate deliveryDate,
    OrderStatus status,
    String planName,
    String menuName,
    Integer mealQuantity,
    String menuDescription,
    String allergenInfo,
    String nutritionInfo,
    String ingredientInfo,
    String recipientName,
    String recipientPhone,
    String postalCode,
    String addressLine1,
    String addressLine2,
    String deliveryMethodCode,
    String otherDeliveryRequest,
    OrderDeliveryTimeSlot deliveryTimeSlot,
    Long amount,
    RefundResponse refund
) {
    public record RefundResponse(
        String refundId,
        RefundStatus status,
        Long requestedAmount,
        Long refundedAmount,
        Long unprocessedAmount
    ) {
    }
}
