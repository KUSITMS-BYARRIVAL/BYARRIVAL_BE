package com.byarrival.domain.home.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 홈 화면 표시 개수.
 *
 * @param smalltalkPreviewSize 스몰토크 추천 미리보기 카드 수(HOME-03)
 * @param popularPreviewSize   출근길 공감대 칩별 순위 수(HOME-05)
 * @param popularDetailSize    출근길 공감대 상세 순위 수(HOME-06)
 */
@ConfigurationProperties(prefix = "home")
public record HomeProperties(int smalltalkPreviewSize, int popularPreviewSize, int popularDetailSize) {

    public HomeProperties {
        if (smalltalkPreviewSize < 1 || popularPreviewSize < 1 || popularDetailSize < popularPreviewSize) {
            throw new IllegalArgumentException("home 표시 개수 설정이 올바르지 않습니다.");
        }
    }
}
