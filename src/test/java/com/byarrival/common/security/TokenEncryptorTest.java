package com.byarrival.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenEncryptorTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void 암호화한_토큰을_복호화하면_원래_값이_나온다() {
        TokenEncryptor encryptor = new TokenEncryptor(new TokenEncryptionProperties(KEY));

        String encrypted = encryptor.encrypt("apple-refresh-token");

        assertThat(encrypted).doesNotContain("apple-refresh-token");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("apple-refresh-token");
    }

    @Test
    void 같은_값도_암호화할_때마다_결과가_다르다() {
        TokenEncryptor encryptor = new TokenEncryptor(new TokenEncryptionProperties(KEY));

        assertThat(encryptor.encrypt("token")).isNotEqualTo(encryptor.encrypt("token"));
    }

    @Test
    void 암호문이_바뀌면_복호화에_실패한다() {
        TokenEncryptor encryptor = new TokenEncryptor(new TokenEncryptionProperties(KEY));
        byte[] data = Base64.getDecoder().decode(encryptor.encrypt("token"));
        data[data.length - 1] ^= 1;

        assertThatThrownBy(() -> encryptor.decrypt(Base64.getEncoder().encodeToString(data)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키가_없으면_꺼져_있고_암호화하지_않는다() {
        TokenEncryptor encryptor = new TokenEncryptor(new TokenEncryptionProperties(""));

        assertThat(encryptor.isEnabled()).isFalse();
        assertThatThrownBy(() -> encryptor.encrypt("token")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void AES_키_길이가_아니면_앱이_시작되지_않는다() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[10]);

        assertThatThrownBy(() -> new TokenEncryptor(new TokenEncryptionProperties(shortKey)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TokenEncryptor(new TokenEncryptionProperties("not base64!")))
            .isInstanceOf(IllegalStateException.class);
    }
}
