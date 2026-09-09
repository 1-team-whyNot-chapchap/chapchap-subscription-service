package com.chapchap.subscription.domain.payment.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class BillingKeyProtector {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";

    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8;

    private static final String AAD_CONTEXT = "payment-method-billing-key:";

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public BillingKeyProtector(@Value("${payment.billing-key.encryption-key}") String encodedKey) {
        byte[] keyBytes = decodeEncryptionKey(encodedKey);

        try {
            validateKeyLength(keyBytes);
            this.secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    public String protect(Long userId, String billingKey) {
        validateUserId(userId);
        validateBillingKey(billingKey);

        try {
            byte[] iv = createIv();

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            cipher.updateAAD(createAad(userId));

            byte[] encrypted = cipher.doFinal(billingKey.getBytes(StandardCharsets.UTF_8));

            byte[] payload = ByteBuffer
                    .allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();

            return Base64.getEncoder().encodeToString(payload);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("빌링키 암호화에 실패했습니다", e);
        }
    }

    public String unprotect(Long userId, String protectedBillingKey) {
        validateUserId(userId);
        validateProtectedBillingKey(protectedBillingKey);

        try {
            byte[] payload = Base64.getDecoder().decode(protectedBillingKey);

            validatePayload(payload);

            ByteBuffer buffer = ByteBuffer.wrap(payload);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            cipher.updateAAD(createAad(userId));

            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );

        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("빌링키 복호화에 실패했습니다", e);
        }
    }

    private byte[] createIv() {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }

    private byte[] createAad(Long userId) {
        return (AAD_CONTEXT + userId).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] decodeEncryptionKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("빌링키 암호화 키가 설정되지 않았습니다");
        }

        try {
            return Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("빌링키 암호화 키는 Base64 형식이어야 합니다", e);
        }
    }

    private void validateKeyLength(byte[] keyBytes) {
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("빌링키 암호화 키는 256비트(32바이트)여야 합니다");
        }
    }

    private void validatePayload(byte[] payload) {
        if (payload.length <= IV_LENGTH_BYTES + TAG_LENGTH_BYTES) {
            throw new IllegalStateException("보호된 빌링키 데이터 형식이 올바르지 않습니다");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 식별자는 1 이상의 값이어야 합니다");
        }
    }

    private void validateBillingKey(String billingKey) {
        if (billingKey == null || billingKey.isBlank()) {
            throw new IllegalArgumentException("빌링키는 비어 있을 수 없습니다");
        }
    }

    private void validateProtectedBillingKey(String protectedBillingKey) {
        if (protectedBillingKey == null || protectedBillingKey.isBlank()) {
            throw new IllegalArgumentException("보호된 빌링키는 비어 있을 수 없습니다");
        }
    }
}