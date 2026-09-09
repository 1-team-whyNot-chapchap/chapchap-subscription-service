package com.chapchap.subscription.domain.subscription.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class KstReferenceTimeProviderTest {

    @Test
    void 처리_기준_시각을_KST와_microsecond_정밀도로_한_번_생성한다() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-09-03T05:00:00.123456789Z"),
                ZoneOffset.UTC
        );
        KstReferenceTimeProvider provider = new KstReferenceTimeProvider(fixedClock);

        assertThat(provider.now()).hasToString("2026-09-03T14:00:00.123456");
    }
}
