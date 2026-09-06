package com.chapchap.subscription.global.validation;

import java.util.regex.Pattern;

/** Subscription Service가 소유하는 공개 식별자의 공통 UUID v4 형식이다. */
public final class PublicIdFormat {
    public static final String UUID_V4_REGEX =
        "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    private static final Pattern UUID_V4_PATTERN = Pattern.compile(UUID_V4_REGEX);

    private PublicIdFormat() {
    }

    public static boolean isUuidV4(String value) {
        return value != null && UUID_V4_PATTERN.matcher(value).matches();
    }
}
