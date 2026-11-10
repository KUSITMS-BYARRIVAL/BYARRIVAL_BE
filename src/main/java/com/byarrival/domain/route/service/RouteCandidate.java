package com.byarrival.domain.route.service;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import java.math.BigDecimal;

/**
 * 노선 추천 후보 기사. 난이도와 소수점 포함 예상 읽기시간(분)을 가진다.
 */
public record RouteCandidate(Long articleId, ArticleDifficulty difficulty, BigDecimal readingMinutes) {
}
