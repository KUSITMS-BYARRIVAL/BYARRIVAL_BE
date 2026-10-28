package com.byarrival.domain.auth.client;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.auth.exception.AuthErrorCode;
import com.byarrival.domain.user.entity.SocialProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Apple identity token(RS256 JWT)을 Apple 공개키로 검증한다. 서명, iss, aud, exp를 확인한다.
 */
@Slf4j
@Component
public class AppleAuthClient implements SocialAuthClient {

    private static final String SIGNING_ALGORITHM = "RS256";
    private static final String KEY_TYPE = "RSA";

    private final RestClient restClient;
    private final SocialAuthProperties.Apple properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AppleAuthClient(RestClient socialAuthRestClient, SocialAuthProperties properties, JsonMapper jsonMapper,
        Clock clock) {
        this.restClient = socialAuthRestClient;
        this.properties = properties.apple();
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.APPLE;
    }

    @Override
    public String getProviderUserId(String identityToken) {
        String[] parts = identityToken.split("\\.", -1);
        if (parts.length != 3) {
            throw authFailed();
        }
        try {
            AppleTokenHeader header = readPart(parts[0], AppleTokenHeader.class);
            if (!SIGNING_ALGORITHM.equals(header.alg()) || header.kid() == null) {
                throw authFailed();
            }
            verifySignature(parts, findPublicKey(header.kid()));

            AppleTokenClaims claims = readPart(parts[1], AppleTokenClaims.class);
            validateClaims(claims);
            return claims.sub();
        } catch (IllegalArgumentException | JacksonException | GeneralSecurityException e) {
            log.warn("Apple identity token 검증 실패: {}", e.getClass().getSimpleName());
            throw authFailed();
        }
    }

    private <T> T readPart(String encodedPart, Class<T> type) {
        return jsonMapper.readValue(Base64.getUrlDecoder().decode(encodedPart), type);
    }

    private PublicKey findPublicKey(String kid) throws GeneralSecurityException {
        AppleKey key = fetchKeys().stream()
            .filter(candidate -> kid.equals(candidate.kid()) && KEY_TYPE.equals(candidate.kty()))
            .findFirst()
            .orElseThrow(this::authFailed);

        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(key.n()));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(key.e()));
        return KeyFactory.getInstance(KEY_TYPE).generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private List<AppleKey> fetchKeys() {
        try {
            AppleKeysResponse response = restClient.get()
                .uri(properties.keysUri())
                .retrieve()
                .body(AppleKeysResponse.class);
            if (response == null || response.keys() == null) {
                throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
            }
            return response.keys();
        } catch (RestClientException e) {
            log.warn("Apple 공개키 조회 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
    }

    private void verifySignature(String[] parts, PublicKey publicKey) throws GeneralSecurityException {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(Base64.getUrlDecoder().decode(parts[2]))) {
            throw authFailed();
        }
    }

    // 클라이언트 ID 설정이 비어 있으면 어떤 토큰도 통과시키지 않는다.
    private void validateClaims(AppleTokenClaims claims) {
        boolean valid = properties.issuer().equals(claims.iss())
            && properties.clientId() != null && !properties.clientId().isBlank()
            && properties.clientId().equals(claims.aud())
            && claims.exp() != null && Instant.ofEpochSecond(claims.exp()).isAfter(clock.instant())
            && claims.sub() != null && !claims.sub().isBlank();
        if (!valid) {
            throw authFailed();
        }
    }

    private BusinessException authFailed() {
        return new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AppleTokenHeader(String kid, String alg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AppleTokenClaims(String iss, String aud, Long exp, String sub) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AppleKeysResponse(List<AppleKey> keys) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AppleKey(String kid, String kty, String n, String e) {
    }
}
