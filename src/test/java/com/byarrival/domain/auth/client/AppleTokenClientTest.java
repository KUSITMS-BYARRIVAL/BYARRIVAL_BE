package com.byarrival.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class AppleTokenClientTest {

    private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URI = "https://appleid.apple.com/auth/revoke";
    private static final String CLIENT_ID = "com.byarrival.app";
    private static final String TEAM_ID = "TEAM123456";
    private static final String KEY_ID = "KEY1234567";
    private static final Instant NOW = Instant.parse("2026-11-15T03:00:00Z");

    private KeyPair keyPair;
    private MockRestServiceServer server;
    private AppleTokenClient client;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        client = client(p8Base64());
    }

    @Test
    void authorization_code를_Apple_refresh_token으로_바꾼다() {
        server.expect(requestTo(TOKEN_URI))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().formDataContains(
                Map.of("client_id", CLIENT_ID, "code", "auth-code", "grant_type", "authorization_code")))
            .andRespond(withSuccess("{\"access_token\":\"a\",\"refresh_token\":\"apple-refresh\",\"id_token\":\"i\"}",
                MediaType.APPLICATION_JSON));

        assertThat(client.exchangeRefreshToken("auth-code")).isEqualTo("apple-refresh");
        server.verify();
    }

    @Test
    void refresh_token으로_Apple_연결을_끊는다() {
        server.expect(requestTo(REVOKE_URI))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().formDataContains(
                Map.of("client_id", CLIENT_ID, "token", "apple-refresh", "token_type_hint", "refresh_token")))
            .andRespond(withSuccess());

        client.revoke("apple-refresh");

        server.verify();
    }

    @Test
    void client_secret은_로그인용_키로_서명한_ES256_JWT다() throws Exception {
        SignedJWT jwt = SignedJWT.parse(client.clientSecret());

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(TEAM_ID);
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getJWTClaimsSet().getAudience()).isEqualTo(List.of("https://appleid.apple.com"));
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isAfter(NOW);
    }

    @Test
    void Apple이_실패_응답을_주면_예외가_난다() {
        server.expect(requestTo(TOKEN_URI))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_grant\"}"));

        assertThatThrownBy(() -> client.exchangeRefreshToken("used-code")).isInstanceOf(RestClientException.class);
    }

    @Test
    void 키_설정이_하나라도_없으면_설정되지_않은_상태다() {
        assertThat(client.isConfigured()).isTrue();
        assertThat(client("").isConfigured()).isFalse();
    }

    private AppleTokenClient client(String privateKeyBase64) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SocialAuthProperties properties = new SocialAuthProperties(Duration.ofSeconds(3), Duration.ofSeconds(5), null,
            new SocialAuthProperties.Apple(null, null, CLIENT_ID, TOKEN_URI, REVOKE_URI, TEAM_ID, KEY_ID,
                privateKeyBase64));
        return new AppleTokenClient(builder.build(), properties, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    }

    // Apple이 내려주는 .p8 파일과 같은 PEM 형식을 만든 뒤, 설정처럼 파일 전체를 base64로 감싼다.
    private String p8Base64() {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));
    }
}
