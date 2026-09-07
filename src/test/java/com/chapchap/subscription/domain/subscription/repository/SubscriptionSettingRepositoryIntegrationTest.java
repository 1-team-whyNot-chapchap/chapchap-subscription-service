package com.chapchap.subscription.domain.subscription.repository;

import com.chapchap.subscription.domain.subscription.entity.SubscriptionSetting;
import com.chapchap.subscription.domain.subscription.entity.SubscriptionSettingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionSettingRepositoryIntegrationTest {

    @Autowired
    private SubscriptionSettingRepository settings;

    @Test
    void 동일_적용일에_대체된_설정은_조회에서_제외하고_최신_설정만_반환한다() {
        long subscriptionId = ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
        LocalDate effectiveDate = LocalDate.of(2026, 9, 9);

        SubscriptionSetting superseded = SubscriptionSetting.createAwaitingConfirmation(
            subscriptionId, 1L, 2, LocalDateTime.of(2026, 9, 7, 12, 0), effectiveDate
        );
        superseded.activate(LocalDateTime.of(2026, 9, 7, 12, 1));
        superseded.closeAt(effectiveDate);
        settings.saveAndFlush(superseded);

        SubscriptionSetting latest = SubscriptionSetting.createAwaitingConfirmation(
            subscriptionId, 2L, 3, LocalDateTime.of(2026, 9, 7, 13, 0), effectiveDate
        );
        latest.activate(LocalDateTime.of(2026, 9, 7, 13, 1));
        settings.saveAndFlush(latest);

        var applicable = settings.findApplicableSettings(
            subscriptionId, SubscriptionSettingStatus.ACTIVE, effectiveDate
        );

        assertThat(applicable).containsExactly(latest);
    }
}
