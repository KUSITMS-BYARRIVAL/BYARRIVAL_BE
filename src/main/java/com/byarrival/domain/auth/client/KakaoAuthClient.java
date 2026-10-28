package com.byarrival.domain.auth.client;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.auth.exception.AuthErrorCode;
import com.byarrival.domain.user.entity.SocialProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 access token을 토큰 정보 API로 검증한다. 우리 앱에서 발급된 토큰인지(app_id)도 확인한다.
 */
@Slf4j
@Component
public class KakaoAuthClient implements SocialAuthClient {

    private final RestClient restClient;
    private final SocialAuthProperties.Kakao properties;

    public KakaoAuthClient(RestClient socialAuthRestClient, SocialAuthProperties properties) {
        this.restClient = socialAuthRestClient;
        this.properties = properties.kakao();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public String getProviderUserId(String accessToken) {
        KakaoTokenInfoResponse tokenInfo = fetchTokenInfo(accessToken);
        if (tokenInfo == null || tokenInfo.id() == null || !isIssuedForThisApp(tokenInfo)) {
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
        return String.valueOf(tokenInfo.id());
    }

    private KakaoTokenInfoResponse fetchTokenInfo(String accessToken) {
        try {
            return restClient.get()
                .uri(properties.tokenInfoUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(KakaoTokenInfoResponse.class);
        } catch (HttpClientErrorException e) {
            log.warn("카카오 토큰 검증 실패: status={}", e.getStatusCode());
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        } catch (RestClientException e) {
            log.warn("카카오 토큰 정보 API 호출 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
    }

    // 앱 ID 설정이 비어 있으면 어떤 토큰도 통과시키지 않는다.
    private boolean isIssuedForThisApp(KakaoTokenInfoResponse tokenInfo) {
        return properties.appId() != null && properties.appId().equals(tokenInfo.appId());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoTokenInfoResponse(Long id, @JsonProperty("app_id") Long appId) {
    }
}
