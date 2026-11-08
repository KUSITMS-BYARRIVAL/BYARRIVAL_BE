package com.byarrival.common.security;

import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 액세스 토큰 만료 검증. 만료는 TOKEN_EXPIRED 코드로 구분해 앱이 재발급을 시도할 수 있게 한다.
 */
public class JwtExpiryValidator implements OAuth2TokenValidator<Jwt> {

    public static final String TOKEN_EXPIRED = "token_expired";

    private final Clock clock;

    public JwtExpiryValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN));
        }
        if (!expiresAt.isAfter(Instant.now(clock))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(TOKEN_EXPIRED));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
