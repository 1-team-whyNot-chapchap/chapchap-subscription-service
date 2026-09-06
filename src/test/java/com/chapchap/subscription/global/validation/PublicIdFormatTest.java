package com.chapchap.subscription.global.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicIdFormatTest {

    @Test
    void 소문자_표준_UUID_v4만_허용한다() {
        assertThat(PublicIdFormat.isUuidV4("550e8400-e29b-41d4-a716-446655440000")).isTrue();

        assertThat(PublicIdFormat.isUuidV4("PAY-550e8400-e29b-41d4-a716-446655440000")).isFalse();
        assertThat(PublicIdFormat.isUuidV4("550E8400-E29B-41D4-A716-446655440000")).isFalse();
        assertThat(PublicIdFormat.isUuidV4("550e8400-e29b-11d4-a716-446655440000")).isFalse();
        assertThat(PublicIdFormat.isUuidV4(null)).isFalse();
    }
}
