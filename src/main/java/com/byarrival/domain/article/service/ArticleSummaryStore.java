package com.byarrival.domain.article.service;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.entity.ArticleContent;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
import com.byarrival.domain.article.repository.ArticleContentRepository;
import com.byarrival.domain.article.repository.ArticleRepository;
import com.byarrival.domain.article.service.ArticleSummaryValidator.ValidatedSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    /**
     * 재생성(ARTICLE-06)할 기사와, 남아 있으면 정제한 본문. 요약을 마친 기사는 본문을 지웠으므로 비어 있다.
     */
    @Transactional(readOnly = true)
    public Optional<RegenerationSource> loadForRegeneration(Long articleId) {
        return articleRepository.findById(articleId).map(article -> new RegenerationSource(article.getId(),
            article.getTitle(), article.linkUrl(), article.getCategoryId(), article.getServiceDate(),
            article.getDifficulty(), article.getReadingMinutes(),
            articleContentRepository.findById(articleId).map(ArticleContent::getContent).orElse(null)));
    }

    /**
     * 난이도가 없는 기사(난이도 판정 전에 실패한 기사)에 줄 난이도. 그 서비스일 카테고리의 반반 비율이 덜 깨지도록 적은 쪽을 준다.
     * 같으면 EASY다.
     */
    @Transactional(readOnly = true)
    public ArticleDifficulty lessUsedDifficulty(Long categoryId, LocalDate serviceDate) {
        long hard = 0;
        long easy = 0;
        for (ArticleRepository.CategoryDifficultyCount count
            : articleRepository.countDifficultyByCategory(serviceDate, ArticleProcessStatus.READY)) {
            if (!categoryId.equals(count.getCategoryId()) || count.getDifficulty() == null) {
                continue;
            }
            if (count.getDifficulty() == ArticleDifficulty.HARD) {
                hard += count.getArticleCount();
            } else {
                easy += count.getArticleCount();
            }
        }
        return hard < easy ? ArticleDifficulty.HARD : ArticleDifficulty.EASY;
    }

    /**
     * 재생성한 요약으로 덮어쓴다(이력 없음). 본문을 다시 가져왔고 아직 정제 단계를 마치지 못한 기사면 원문 주소와 읽기시간도 채운다.
     * 남아 있던 본문은 지운다.
     *
     * @param sourceUrl      다시 가져온 원문 주소. 남아 있던 본문을 썼으면 null
     * @param readingMinutes 다시 가져온 본문의 읽기시간. 남아 있던 본문을 썼으면 null
     */
    @Transactional
    public void overwrite(Long articleId, ArticleDifficulty difficulty, ValidatedSummary summary, String sourceUrl,
        BigDecimal readingMinutes) {
        Article article = articleRepository.findById(articleId).orElseThrow();
        if (sourceUrl != null && (article.getSourceUrl() == null || article.getReadingMinutes() == null)) {
            article.markCleaned(sourceUrl, readingMinutes);
        }
        article.markSummarized(difficulty, summary.oneLine(), summary.sentences(), summary.keywords());
        if (articleContentRepository.existsById(articleId)) {
            articleContentRepository.deleteById(articleId);
        }
    }

    public record SummaryInput(Long articleId, String title, String content) {
    }

    /**
     * @param link    다시 가져올 때 쓰는 주소. 원문 주소가 없으면 수집한 주소다
     * @param content 남아 있는 정제 본문. 없으면 null
     */
    public record RegenerationSource(Long articleId, String title, String link, Long categoryId,
                                     LocalDate serviceDate, ArticleDifficulty difficulty, BigDecimal readingMinutes,
                                     String content) {
    }

    public record PendingArticle(Long articleId, Long categoryId, String title, String content) {
    }
}
