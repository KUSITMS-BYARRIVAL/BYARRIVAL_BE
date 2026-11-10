package com.byarrival.domain.article.service;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
import com.byarrival.domain.article.repository.ArticleContentRepository;
import com.byarrival.domain.article.repository.ArticleRepository;
import com.byarrival.domain.article.service.ArticleSummaryValidator.ValidatedSummary;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 가공 입력을 읽고 결과를 저장한다. AI 호출이 트랜잭션 밖에서 일어나도록 읽기와 쓰기를 따로 묶는다.
 */
@Component
@RequiredArgsConstructor
public class ArticleSummaryStore {

    private final ArticleRepository articleRepository;
    private final ArticleContentRepository articleContentRepository;

    /**
     * 정제가 끝났고 아직 요약하지 않은 기사만 읽는다. 이미 요약한 기사는 다시 호출하지 않는다.
     */
    @Transactional(readOnly = true)
    public List<SummaryInput> loadInputs(Collection<Long> articleIds) {
        return articleContentRepository.findAllWithArticle(articleIds, ArticleProcessStatus.CLEANED).stream()
            .map(content -> new SummaryInput(content.getArticleId(), content.getArticle().getTitle(),
                content.getContent()))
            .toList();
    }

    /**
     * 서비스일에 정제가 끝나 AI 가공을 기다리는 기사. 카테고리가 매핑된 기사만 정제하므로 카테고리가 늘 있다.
     */
    @Transactional(readOnly = true)
    public List<PendingArticle> loadPending(LocalDate serviceDate) {
        return articleContentRepository.findAllPending(serviceDate, ArticleProcessStatus.CLEANED).stream()
            .map(content -> new PendingArticle(content.getArticleId(), content.getArticle().getCategoryId(),
                content.getArticle().getTitle(), content.getContent()))
            .toList();
    }

    /**
     * 서비스일에 이미 요약을 마친 기사의 카테고리별 난이도 수. 배치를 다시 돌릴 때 반반 비율을 맞추는 데 쓴다.
     */
    @Transactional(readOnly = true)
    public List<ArticleRepository.CategoryDifficultyCount> countSummarizedDifficulty(LocalDate serviceDate) {
        return articleRepository.countDifficultyByCategory(serviceDate, ArticleProcessStatus.READY);
    }

    /**
     * 요약을 저장하고 추천 후보로 올린 뒤 원문 본문을 지운다. 본문은 AI 가공에만 쓰는 임시 데이터다.
     *
     * @return 저장했으면 true. 그 사이 다른 실행이 먼저 요약했으면 false
     */
    @Transactional
    public boolean save(Long articleId, ArticleDifficulty difficulty, ValidatedSummary summary) {
        Article article = articleRepository.findById(articleId).orElse(null);
        if (article == null || article.getProcessStatus() != ArticleProcessStatus.CLEANED) {
            return false;
        }
        article.markSummarized(difficulty, summary.oneLine(), summary.sentences(), summary.keywords());
        articleContentRepository.deleteById(articleId);
        return true;
    }

    public record SummaryInput(Long articleId, String title, String content) {
    }

    public record PendingArticle(Long articleId, Long categoryId, String title, String content) {
    }
}
