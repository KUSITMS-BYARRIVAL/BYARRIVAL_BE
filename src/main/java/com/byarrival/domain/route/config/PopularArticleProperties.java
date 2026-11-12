package com.byarrival.domain.route.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 많이 담은 뉴스 순위 설정값.
 *
 * @param minUsers 순위를 보여 주는 최소 인원. 그날 기사를 1건 이상 담은 사용자가 이보다 적으면 집계중 상태다(HOME-07)
 */
@ConfigurationProperties(prefix = "route.popular")
public record PopularArticleProperties(int minUsers) {

    public PopularArticleProperties {
        if (minUsers < 1) {
            throw new IllegalArgumentException("route.popular.min-users는 1 이상이어야 합니다.");
        }
    }
}
