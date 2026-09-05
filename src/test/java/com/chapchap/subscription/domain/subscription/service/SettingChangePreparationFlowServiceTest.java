package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.service.SettingChangeOrderPreparationCommand;
import com.chapchap.subscription.domain.subscription.entity.Subscription;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingChangePreparationFlowServiceTest {

    @Test
    void 검증후_주문계획을_만들고_변경대기_데이터를_함께_저장한다() {
        SettingChangePreparationService preparation = mock(SettingChangePreparationService.class);
        SettingChangeOrderPlanService plan = mock(SettingChangeOrderPlanService.class);
        SettingChangePendingSaveService save = mock(SettingChangePendingSaveService.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SettingChangePreparationFlowService service = new SettingChangePreparationFlowService(preparation, plan, save, subscriptions);
        SettingChangePreparationRequest request = mock(SettingChangePreparationRequest.class);
        SettingChangePreparationResult prepared = mock(SettingChangePreparationResult.class);
        SettingChangeOrderPreparationCommand orderPlan = mock(SettingChangeOrderPreparationCommand.class);
        when(preparation.prepare(10L, request)).thenReturn(prepared);
        when(subscriptions.findWithLockByUserId(10L)).thenReturn(Optional.of(mock(Subscription.class)));
        when(plan.plan(prepared)).thenReturn(orderPlan);
        when(save.save(prepared, orderPlan)).thenReturn(new SettingChangePendingSaveResult(3L, 2, List.of(4L, 5L)));
        when(prepared.subscriptionId()).thenReturn(1L);
        when(prepared.referenceAt()).thenReturn(LocalDateTime.of(2026, 9, 7, 13, 0));
        when(prepared.effectiveStartDate()).thenReturn(LocalDate.of(2026, 9, 8));

        PreparedSettingChange result = service.prepare(10L, request);

        InOrder inOrder = inOrder(subscriptions, preparation, plan, save);
        inOrder.verify(subscriptions).findWithLockByUserId(10L);
        inOrder.verify(preparation).prepare(10L, request);
        inOrder.verify(plan).plan(prepared);
        inOrder.verify(save).save(prepared, orderPlan);
        assertThat(result).isEqualTo(new PreparedSettingChange(
            1L, 3L, 2, LocalDateTime.of(2026, 9, 7, 13, 0), LocalDate.of(2026, 9, 8), 2
        ));
    }
}
