package com.byarrival.domain.article.service;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.repository.ArticleRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 사건 정리의 입력을 읽고 결과를 저장한다. AI 호출이 트랜잭션 밖에서 일어나도록 읽기와 쓰기를 따로 묶는다.
 */
@Component
@RequiredArgsConstructor
public class ArticleStoryStore {

    private final ArticleRepository articleRepository;

    @Transactional(readOnly = true)
    public List<Article> loadRecommendable(LocalDate serviceDate) {
        return articleRepository.findAllByServiceDateAndRecommendableTrueOrderById(serviceDate);
    }

    /**
     * @param duplicates 추천에서 뺄 기사 ID → 남길 기사 ID
     * @return 실제로 뺀 기사 수. 그 사이 이미 추천에서 빠진 기사는 건너뛴다
     */
    @Transactional
    public int markDuplicates(Map<Long, Long> duplicates) {
        int marked = 0;
        for (Article article : articleRepository.findAllById(duplicates.keySet())) {
            if (article.isRecommendable()) {
                article.markDuplicateOf(duplicates.get(article.getId()));
                marked++;
            }
        }
        return marked;
    }
}
