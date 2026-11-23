package com.byarrival.domain.admin.security;

import com.byarrival.common.security.JwtExpiryValidator;
import com.byarrival.common.security.JwtProperties;
import com.byarrival.domain.admin.config.AdminAuthProperties;
import com.byarrival.domain.admin.entity.AdminAccount;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 어드민 토큰 발급·검증. 앱 토큰과 서명 키를 나눠 서로의 API에 쓸 수 없게 한다.
 * 키는 JWT_SECRET에서 파생하므로 환경 변수를 늘리지 않는다. 인코더·디코더를 빈으로 두면 앱 인증 설정과 겹치므로 여기서만 쓴다.
 */
@Component
public class AdminTokenProvider {

    public static final String TOKEN_VERSION_CLAIM = "ver";
    private static final String KEY_LABEL = "byarrival-admin-token-v1";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AdminAuthProperties properties;
    private final Clock clock;

    public AdminTokenProvider(JwtProperties jwtProperties, AdminAuthProperties properties, Clock clock) {
        SecretKey key = deriveKey(jwtProperties.secret());
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbusDecoder.setJwtValidator(new JwtExpiryValidator(clock));
        this.decoder = nimbusDecoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String create(AdminAccount account) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(String.valueOf(account.getId()))
            .issuedAt(now)
            .expiresAt(now.plus(properties.sessionValidity()))
            .claim(TOKEN_VERSION_CLAIM, account.getTokenVersion())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * @throws org.springframework.security.oauth2.jwt.JwtException 서명이 틀리거나 만료된 토큰
     */
    public Jwt decode(String token) {
        return decoder.decode(token);
    }

    private static SecretKey deriveKey(String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return new SecretKeySpec(mac.doFinal(KEY_LABEL.getBytes(StandardCharsets.UTF_8)), HMAC_SHA256);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("어드민 토큰 키를 만들 수 없습니다.", e);
        }
    }
}
