package com.byarrival.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 외부 서비스 토큰을 AES-GCM으로 암호화해 저장한다. 결과는 base64(IV 12바이트 + 암호문)이다.
 * 키가 없으면 isEnabled()가 false이고, 호출하는 쪽은 토큰을 저장하지 않는다.
 */
@Component
@EnableConfigurationProperties(TokenEncryptionProperties.class)
public class TokenEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenEncryptor(TokenEncryptionProperties properties) {
        this.key = toKey(properties.key());
    }

    public boolean isEnabled() {
        return key != null;
    }

    public String encrypt(String plainText) {
        requireEnabled();
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("토큰을 암호화할 수 없습니다.", e);
        }
    }

    public String decrypt(String encryptedText) {
        requireEnabled();
        try {
            byte[] data = Base64.getDecoder().decode(encryptedText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("토큰을 복호화할 수 없습니다.", e);
        }
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("token-encryption.key가 설정되지 않았습니다.");
        }
    }

    // 키 형식이 틀리면 운영에서 늦게 발견하지 않도록 앱 시작 때 실패한다.
    private static SecretKey toKey(String encodedKey) {
        if (!StringUtils.hasText(encodedKey)) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encodedKey.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("token-encryption.key는 base64여야 합니다.", e);
        }
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
            throw new IllegalStateException("token-encryption.key는 16·24·32바이트 AES 키여야 합니다.");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
