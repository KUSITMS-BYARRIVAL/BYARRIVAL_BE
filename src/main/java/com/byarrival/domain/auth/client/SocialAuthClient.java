package com.byarrival.domain.auth.client;

import com.byarrival.domain.user.entity.SocialProvider;

/**
 * 소셜 Provider 토큰을 검증하고 소셜 계정 식별자를 얻는다.
 */
public interface SocialAuthClient {

    SocialProvider provider();

    /**
     * @return Provider가 발급한 사용자 식별자
     * @throws com.byarrival.common.exception.BusinessException 토큰이 유효하지 않거나 Provider 서버에 연결할 수 없을 때
     */
    String getProviderUserId(String token);
}
