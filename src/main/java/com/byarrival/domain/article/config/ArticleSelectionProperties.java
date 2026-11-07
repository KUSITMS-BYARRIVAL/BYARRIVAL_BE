package com.byarrival.domain.article.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서비스일마다 카테고리별로 고르는 기사 기준. 수집 시점 Feed 순서(주요도)로 위에서부터 고른다.
 *
 * @param maxArticlesPerCategory 카테고리별 최대 기사 수
 * @param similarTitleThreshold  이미 고른 기사와 제목 유사도(글자 2-gram Dice)가 이 값 이상이면 같은 사건으로 본다(0~1)
 */
@ConfigurationProperties(prefix = "rss.selection")
public record ArticleSelectionProperties(int maxArticlesPerCategory, double similarTitleThreshold) {

    public ArticleSelectionProperties {
        if (maxArticlesPerCategory <= 0) {
            throw new IllegalArgumentException("rss.selection.max-articles-per-category는 0보다 커야 합니다.");
        }
    }
}
