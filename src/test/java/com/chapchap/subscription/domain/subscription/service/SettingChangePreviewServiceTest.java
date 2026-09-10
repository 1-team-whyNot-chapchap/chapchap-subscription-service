package com.chapchap.subscription.domain.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chapchap.subscription.domain.order.service.SettingChangeOrderPreparationCommand;
import com.chapchap.subscription.domain.order.service.SettingChangeOrderService;
import com.chapchap.subscription.domain.payment.entity.PaymentAllocation;
import com.chapchap.subscription.domain.payment.repository.PaymentAllocationRepository;
import com.chapchap.subscription.domain.subscription.request.SettingChangeRequest;
import com.chapchap.subscription.domain.subscription.response.SettingChangePreviewResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingChangePreviewServiceTest {

    @Test
    void 미리보기는_변경을_저장하지_않고_증액_금액을_계산한다() {
        SettingChangePreparationService preparation = mock(SettingChangePreparationService.class);
        SettingChangeOrderPlanService orderPlans = mock(SettingChangeOrderPlanService.class);
        SettingChangeOrderService orders = mock(SettingChangeOrderService.class);
        PaymentAllocationRepository allocations = mock(PaymentAllocationRepository.class);
        SettingChangePreviewService service = new SettingChangePreviewService(preparation, orderPlans, orders, allocations);
        LocalDate effectiveStartDate = LocalDate.of(2026, 9, 8);
        SettingChangePreparationResult prepared = new SettingChangePreparationResult(
            10L, 1L, 3L, 4L, LocalDateTime.of(2026, 9, 6, 12, 0), effectiveStartDate,
            new SettingChangeDraft(2, 4L, effectiveStartDate, List.of()), List.of(100L)
        );
        SettingChangeOrderPreparationCommand orderPlan = new SettingChangeOrderPreparationCommand(
            10L, 1L, null, 5L,
            new SettingChangeOrderPreparationCommand.PlanSnapshot(4L, "가정식", 8_900L),
            SettingChangeOrderPreparationCommand.PricingPolicy.RECALCULATE_WITHOUT_DISCOUNT, List.of()
        );
        PaymentAllocation allocation = mock(PaymentAllocation.class);
        when(allocation.currentCancelableAmount()).thenReturn(10_000L);
        when(preparation.prepare(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(prepared);
        when(orderPlans.plan(prepared)).thenReturn(orderPlan);
        when(allocations.findAllByOrderIdInOrderByIdAsc(List.of(100L))).thenReturn(List.of(allocation));
        when(orders.calculatePlannedAmount(orderPlan)).thenReturn(13_000L);

        SettingChangePreviewResponse response = service.preview(10L, new SettingChangeRequest("PLN", List.of()));

        assertThat(response.differenceType()).isEqualTo(SettingChangePreviewResponse.DifferenceType.INCREASE);
        assertThat(response.differenceAmount()).isEqualTo(3_000L);
        assertThat(response.requiredAction()).isEqualTo(SettingChangePreviewResponse.RequiredAction.ADDITIONAL_PAYMENT);
        verify(preparation).prepare(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any());
        verify(orderPlans).plan(prepared);
        verify(orders).calculatePlannedAmount(orderPlan);
    }
}
