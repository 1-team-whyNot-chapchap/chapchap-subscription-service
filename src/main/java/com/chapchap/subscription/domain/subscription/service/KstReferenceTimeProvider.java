package com.chapchap.subscription.domain.subscription.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

//
@Component
public class KstReferenceTimeProvider {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public KstReferenceTimeProvider() {
        this(Clock.system(KST));
    }

    KstReferenceTimeProvider(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime now() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), KST);
        return now.withNano(now.getNano() / 1_000 * 1_000);
    }
}
