package com.byarrival.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.stream.Stream;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Sign in with Apple 서버 API를 호출한다. authorization code를 refresh token으로 바꾸고, 탈퇴할 때 그 토큰을 revoke한다.
 * 두 요청 모두 로그인용 .p8 키로 서명한 client secret(ES256 JWT)이 필요하다.
 */
@Component
public class AppleTokenClient {

    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final Duration CLIENT_SECRET_VALIDITY = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final SocialAuthProperties.Apple properties;
    private final Clock clock;
    private volatile ECPrivateKey privateKey;

    public AppleTokenClient(RestClient socialAuthRestClient, SocialAuthProperties properties, Clock clock) {
        this.restClient = socialAuthRestClient;
        this.properties = properties.apple();
        this.clock = clock;
    }

    /**
     * Bundle ID, Team ID, Key ID, .p8 키가 모두 설정돼 있어야 Apple을 호출한다.
     */
    public boolean isConfigured() {
        return Stream.of(properties.clientId(), properties.teamId(), properties.keyId(), properties.privateKeyBase64())
            .allMatch(StringUtils::hasText);
    }

    /**
     * @return Apple refresh token과 이 code를 발급받은 Apple 계정을 확인할 id token.
     *     Apple이 실패 응답을 주거나 호출에 실패하면 RestClientException이 그대로 나간다.
     */
    public AppleTokens exchangeRefreshToken(String authorizationCode) {
        MultiValueMap<String, String> form = baseForm();
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");
        AppleTokenResponse response = restClient.post()
            .uri(properties.tokenUri())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(AppleTokenResponse.class);
        if (response == null || !StringUtils.hasText(response.refreshToken())
            || !StringUtils.hasText(response.idToken())) {
            throw new IllegalStateException("Apple 토큰 응답에 refresh token 또는 id token이 없습니다.");
        }
        return new AppleTokens(response.refreshToken(), response.idToken());
    }

    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = baseForm();
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        restClient.post()
            .uri(properties.revokeUri())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .toBodilessEntity();
    }

    private MultiValueMap<String, String> baseForm() {
        if (!isConfigured()) {
            throw new IllegalStateException("Apple 로그인 키 설정이 없습니다.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", clientSecret());
        return form;
    }

    // 요청마다 짧은 유효기간으로 새로 만든다. Apple은 최대 6개월까지 허용한다.
    String clientSecret() {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(properties.teamId())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(CLIENT_SECRET_VALIDITY)))
            .audience(AUDIENCE)
            .subject(properties.clientId())
            .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(properties.keyId()).build(), claims);
        try {
            jwt.sign(new ECDSASigner(privateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Apple client secret을 서명할 수 없습니다.", e);
        }
        return jwt.serialize();
    }

    private ECPrivateKey privateKey() {
        if (privateKey == null) {
            privateKey = parsePrivateKey(properties.privateKeyBase64());
        }
        return privateKey;
    }

    // .p8 파일은 "-----BEGIN PRIVATE KEY-----"로 감싼 PKCS#8 EC 키다. 파일 전체를 base64로 한 번 더 감싸 설정에 넣는다.
    private static ECPrivateKey parsePrivateKey(String base64OfP8File) {
        try {
            String pem = new String(Base64.getDecoder().decode(base64OfP8File.strip()), StandardCharsets.UTF_8);
            String body = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(body);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("Apple .p8 키를 읽을 수 없습니다.", e);
        }
    }

    public record AppleTokens(String refreshToken, String idToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AppleTokenResponse(@JsonProperty("refresh_token") String refreshToken,
                              @JsonProperty("id_token") String idToken) {
    }
}
