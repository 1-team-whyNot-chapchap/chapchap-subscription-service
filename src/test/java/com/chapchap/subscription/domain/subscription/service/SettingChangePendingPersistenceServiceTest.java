package com.chapchap.subscription.domain.subscription.service;

import com.chapchap.subscription.domain.subscription.entity.DeliveryTimeSlot;
import com.chapchap.subscription.domain.subscription.entity.DeliveryWeekday;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionDeliveryCondition;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionDeliveryConditionRepository;
import com.chapchap.subscription.domain.subscription.repository.SubscriptionSettingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingChangePendingPersistenceServiceTest {

    @Test
    void 변경대기_설정과_배송조건을_저장한다() {
        SubscriptionSettingRepository settings = mock(SubscriptionSettingRepository.class);
        SubscriptionDeliveryConditionRepository conditions = mock(SubscriptionDeliveryConditionRepository.class);
        SettingChangePendingPersistenceService service = new SettingChangePendingPersistenceService(settings, conditions);
        SettingChangePreparationResult prepared = new SettingChangePreparationResult(
            10L,
            1L,
            2L,
            3L,
            LocalDateTime.of(2026, 9, 8, 13, 0),
            LocalDate.of(2026, 9, 9),
            new SettingChangeDraft(
                2,
                3L,
                LocalDate.of(2026, 9, 9),
                List.of(new SettingChangeDraft.DeliveryCondition(
                    DeliveryWeekday.MONDAY, 2, 4L, DeliveryTimeSlot.TIME_1100_1300
                ))
            ),
            List.of(9L)
        );
        when(settings.save(org.mockito.ArgumentMatchers.any(SubscriptionSetting.class))).thenAnswer(invocation -> {
            SubscriptionSetting setting = invocation.getArgument(0);
            ReflectionTestUtils.setField(setting, "id", 10L);
            return setting;
        });

        SettingChangePendingPersistenceResult result = service.persist(prepared);

        ArgumentCaptor<SubscriptionSetting> settingCaptor = ArgumentCaptor.forClass(SubscriptionSetting.class);
        verify(settings).save(settingCaptor.capture());
        assertThat(settingCaptor.getValue().getStatus()).isEqualTo(SubscriptionSettingStatus.CHANGE_PENDING);
        assertThat(settingCaptor.getValue().getEffectiveStartDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        ArgumentCaptor<List<SubscriptionDeliveryCondition>> conditionCaptor = ArgumentCaptor.forClass(List.class);
        verify(conditions).saveAll(conditionCaptor.capture());
        assertThat(conditionCaptor.getValue()).singleElement().satisfies(condition -> {
            assertThat(condition.getSubscriptionSettingId()).isEqualTo(10L);
            assertThat(condition.getAddressId()).isEqualTo(4L);
        });
        assertThat(result).isEqualTo(new SettingChangePendingPersistenceResult(10L, 2));
    }
}
