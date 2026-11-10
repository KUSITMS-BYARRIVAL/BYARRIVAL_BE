package com.byarrival.domain.article.service;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.repository.ArticleRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인(노선 등)이 쓰는 기사 조회
 */
@Service
@RequiredArgsConstructor
public class ArticleQueryService {

    private final ArticleRepository articleRepository;

    /**
     * @return 그 서비스일의 추천 가능 기사 중 주어진 카테고리에 속하고 숨기지 않은 기사
     */
    @Transactional(readOnly = true)
    public List<Article> findRecommendable(LocalDate serviceDate, Collection<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return articleRepository.findRecommendable(serviceDate, categoryIds);
    }

    /**
     * @return 기사 ID → 기사
     */
    @Transactional(readOnly = true)
    public Map<Long, Article> findAllByIds(Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        return articleRepository.findAllById(articleIds).stream()
            .collect(Collectors.toMap(Article::getId, Function.identity()));
    }
}
