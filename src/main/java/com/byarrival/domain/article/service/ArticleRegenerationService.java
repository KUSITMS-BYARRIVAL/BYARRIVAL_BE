package com.byarrival.domain.article.service;

import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.service.ArticleCrawler.CrawlResult;
import com.byarrival.domain.article.service.ArticleSummaryStore.RegenerationSource;
import com.byarrival.domain.article.service.ArticleSummaryStore.SummaryInput;
import com.byarrival.domain.article.service.ArticleSummaryValidator.ValidatedSummary;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 어드민 요약 재생성(ARTICLE-06). 기사 하나의 3줄 요약·한 줄 요약·키워드를 다시 만들어 덮어쓴다.
 * <ul>
 *     <li>요약을 마친 기사는 본문을 지웠으므로 원문을 다시 가져온다. 남아 있으면(요약에 실패한 기사) 그 본문을 쓴다.</li>
 *     <li>난이도는 다시 판정하지 않는다. 난이도가 없는 기사만 그 카테고리에서 적은 쪽 난이도를 준다.</li>
 *     <li>AI 호출은 트랜잭션 밖에서 하고, 검증을 통과한 요약만 저장한다(AI-02).</li>
 * </ul>
 * 노출 여부 같은 상태 확인과 퀴즈 재생성은 호출하는 쪽에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleRegenerationService {

    private final ArticleSummaryStore store;
    private final ArticleSummaryService summaryService;
    private final ArticleCrawler crawler;
    private final ReadingTimeCalculator readingTimeCalculator;

    public SummaryOutcome regenerateSummary(Long articleId) {
        RegenerationSource source = store.loadForRegeneration(articleId).orElse(null);
        if (source == null) {
            return SummaryOutcome.failed(SummaryFailure.NOT_FOUND);
        }
        if (source.categoryId() == null) {
            return SummaryOutcome.failed(SummaryFailure.NO_CATEGORY);
        }
        String content = source.content();
        String sourceUrl = null;
        BigDecimal readingMinutes = null;
        if (content == null) {
            CrawlResult crawled = crawler.crawl(source.link());
            if (crawled.article() == null) {
                log.warn("[AI 요약 재생성] articleId={} 원문을 다시 가져오지 못함", articleId);
                return SummaryOutcome.failed(SummaryFailure.CRAWL_FAILED);
            }
            content = crawled.article().cleanedContent();
            sourceUrl = crawled.article().sourceUrl();
            readingMinutes = readingTimeCalculator.calculate(content);
        }
        ArticleDifficulty difficulty = source.difficulty() != null ? source.difficulty()
            : store.lessUsedDifficulty(source.categoryId(), source.serviceDate());
        ValidatedSummary summary = summaryService.generateOne(
            new SummaryInput(articleId, source.title(), content), difficulty);
        if (summary == null) {
            return SummaryOutcome.failed(SummaryFailure.AI_FAILED);
        }
        store.overwrite(articleId, difficulty, summary, sourceUrl, readingMinutes);
        return SummaryOutcome.succeeded();
    }

    public enum SummaryFailure {
        NOT_FOUND,
        NO_CATEGORY,
        CRAWL_FAILED,
        AI_FAILED
    }

    /**
     * @param failure 실패 사유. 성공했으면 null
     */
    public record SummaryOutcome(boolean success, SummaryFailure failure) {

        static SummaryOutcome succeeded() {
            return new SummaryOutcome(true, null);
        }

        static SummaryOutcome failed(SummaryFailure failure) {
            return new SummaryOutcome(false, failure);
        }
    }
}
