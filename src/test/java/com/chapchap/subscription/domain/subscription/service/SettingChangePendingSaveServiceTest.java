package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.order.entity.Order;
import com.chapchap.subscription.domain.order.service.SettingChangeOrderPreparationCommand;
import com.chapchap.subscription.domain.order.service.SettingChangeOrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingChangePendingSaveServiceTest {

    @Test
    void 저장된_설정_ID를_변경주문_생성에_연결한다() {
        SettingChangePendingPersistenceService settings = mock(SettingChangePendingPersistenceService.class);
        SettingChangeOrderService orders = mock(SettingChangeOrderService.class);
        SettingChangePendingSaveService service = new SettingChangePendingSaveService(settings, orders);
        SettingChangePreparationResult prepared = mock(SettingChangePreparationResult.class);
        SettingChangeDraft draft = mock(SettingChangeDraft.class);
        SettingChangeOrderPreparationCommand orderPlan = mock(SettingChangeOrderPreparationCommand.class);
        SettingChangeOrderPreparationCommand savedOrderPlan = mock(SettingChangeOrderPreparationCommand.class);
        Order order = mock(Order.class);
        when(prepared.subscriptionId()).thenReturn(1L);
        when(prepared.userId()).thenReturn(10L);
        when(prepared.draft()).thenReturn(draft);
        when(draft.planId()).thenReturn(2L);
        when(orderPlan.subscriptionId()).thenReturn(1L);
        when(orderPlan.plan()).thenReturn(new SettingChangeOrderPreparationCommand.PlanSnapshot(2L, "가정식", 10_000L));
        when(settings.persist(prepared)).thenReturn(new SettingChangePendingPersistenceResult(3L, 2));
        when(orderPlan.withSubscriptionSettingId(3L)).thenReturn(savedOrderPlan);
        when(order.getId()).thenReturn(4L);
        when(orders.prepare(savedOrderPlan)).thenReturn(List.of(order));

        SettingChangePendingSaveResult result = service.save(prepared, orderPlan);

        verify(orderPlan).withSubscriptionSettingId(3L);
        assertThat(result).isEqualTo(new SettingChangePendingSaveResult(3L, 2, List.of(4L)));
    }
}
