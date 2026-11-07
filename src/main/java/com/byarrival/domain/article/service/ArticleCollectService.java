package com.byarrival.domain.article.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.exception.ArticleErrorCode;
import com.byarrival.domain.article.service.ArticleCrawler.CrawledArticle;
import com.byarrival.domain.article.service.ArticleSaveService.ArticleCandidate;
import com.byarrival.domain.article.service.ArticleSaveService.CollectPlan;
import com.byarrival.domain.article.service.ArticleSaveService.CollectedArticle;
import com.byarrival.domain.article.service.ArticleStoryMatcher.Story;
import com.byarrival.domain.feed.dto.FetchedFeed;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Feed 하나의 기사를 고르고 원문을 크롤링해 저장한다. 카테고리별로 Feed 순서(수집 시점 주요도)가 높은 기사부터
 * 최대 수까지 고른다. 같은 서비스일에 이미 고른 기사와 같은 사건(다른 언론사가 쓴 기사 포함)이나 같은 원문은 고르지 않고,
 * 원문을 가져오지 못한 자리는 다음 순서 기사로 채운다.
 * RSS description은 본문이 아니므로 원본으로만 보존하고, 읽기시간은 크롤링한 원문 본문으로 계산한다.
 */
@Slf4j
@Service
public class ArticleCollectService {

    private final ArticleSaveService articleSaveService;
    private final ArticleCrawler articleCrawler;
    private final ArticleStoryMatcher storyMatcher;
    private final ReadingTimeCalculator readingTimeCalculator;
    private final ExecutorService crawlExecutor;

    public ArticleCollectService(ArticleSaveService articleSaveService, ArticleCrawler articleCrawler,
        ArticleStoryMatcher storyMatcher, ReadingTimeCalculator readingTimeCalculator,
        @Qualifier("articleCrawlExecutor") ExecutorService crawlExecutor) {
        this.articleSaveService = articleSaveService;
        this.articleCrawler = articleCrawler;
        this.storyMatcher = storyMatcher;
        this.readingTimeCalculator = readingTimeCalculator;
        this.crawlExecutor = crawlExecutor;
    }

    /**
     * 크롤링은 트랜잭션 밖에서 하고, 저장만 한 트랜잭션으로 묶는다. 저장에 실패하면 이 Feed의 기사만 저장되지 않는다.
     *
     * @return 새로 저장한 기사 수
     */
    public int collect(FetchedFeed feed, LocalDate serviceDate) {
        CollectPlan plan = articleSaveService.plan(feed, serviceDate);
        List<CollectedArticle> collected = new ArrayList<>(new Selection(plan, serviceDate).run());
        int crawled = collected.size();
        plan.unmapped().forEach(candidate -> collected.add(new CollectedArticle(candidate.toArticle(serviceDate), null)));
        int saved = articleSaveService.saveAll(collected);
        log.info("[ArticleCollect] feedId={} {}건 저장 (원문 확보 {}건, 카테고리 미매핑 {}건)",
            feed.feedId(), saved, crawled, plan.unmapped().size());
        return saved;
    }

    /**
     * 한 Feed의 선별 상태. 남은 자리만큼 Feed 순서대로 동시에 크롤링하고, 실패하거나 겹친 자리는 다음 후보로 다시 채운다.
     */
    private final class Selection {

        private final LocalDate serviceDate;
        private final Map<Long, Integer> remaining;
        private final Deque<ArticleCandidate> queue;
        private final List<Story> pickedStories;
        private final Set<String> pickedSourceUrls = new HashSet<>();
        private final List<CollectedArticle> collected = new ArrayList<>();
        private int sameStory;
        private int sameSource;

        private Selection(CollectPlan plan, LocalDate serviceDate) {
            this.serviceDate = serviceDate;
            this.remaining = new HashMap<>(plan.remainingByCategory());
            this.queue = new ArrayDeque<>(plan.mapped());
            this.pickedStories = new ArrayList<>(plan.pickedStories());
        }

        private List<CollectedArticle> run() {
            while (true) {
                List<ArticleCandidate> wave = takeWave();
                if (wave.isEmpty()) {
                    if (sameStory > 0 || sameSource > 0) {
                        log.info("[ArticleCollect] 같은 사건 {}건, 같은 원문 {}건 제외", sameStory, sameSource);
                    }
                    return collected;
                }
                List<Future<Optional<CrawledArticle>>> results = wave.stream()
                    .map(candidate -> crawlExecutor.submit(() -> articleCrawler.crawl(candidate.item().link())))
                    .toList();
                List<CrawledArticle> crawled = new ArrayList<>();
                for (Future<Optional<CrawledArticle>> result : results) {
                    crawled.add(await(result).orElse(null));
                }
                accept(wave, crawled);
            }
        }

        // 자리가 남은 카테고리의 후보를 Feed 순서대로 꺼낸다. 이미 고른 기사와 같은 사건이면 버리고,
        // 같은 회차 후보와 같은 사건이면 남겨 둔다(앞 후보가 크롤링에 실패하면 다음 회차에 대신 쓴다).
        private List<ArticleCandidate> takeWave() {
            Map<Long, Integer> slots = new HashMap<>(remaining);
            List<ArticleCandidate> wave = new ArrayList<>();
            Iterator<ArticleCandidate> iterator = queue.iterator();
            while (iterator.hasNext()) {
                ArticleCandidate candidate = iterator.next();
                int slot = slots.getOrDefault(candidate.categoryId(), 0);
                if (slot <= 0) {
                    continue;
                }
                if (isPickedStory(candidate.story())) {
                    iterator.remove();
                    sameStory++;
                    continue;
                }
                if (wave.stream().anyMatch(other -> storyMatcher.isSameStory(other.story(), candidate.story()))) {
                    continue;
                }
                iterator.remove();
                wave.add(candidate);
                slots.put(candidate.categoryId(), slot - 1);
            }
            return wave;
        }

        // 원문을 가져온 기사 중 이미 저장했거나 이번에 고른 원문과 주소가 같은 기사는 버린다
        private void accept(List<ArticleCandidate> wave, List<CrawledArticle> crawled) {
            Set<String> existing = articleSaveService.findExistingSourceUrls(crawled.stream()
                .filter(Objects::nonNull)
                .map(CrawledArticle::sourceUrl)
                .toList());
            for (int i = 0; i < wave.size(); i++) {
                CrawledArticle article = crawled.get(i);
                if (article == null) {
                    continue;
                }
                if (existing.contains(article.sourceUrl()) || !pickedSourceUrls.add(article.sourceUrl())) {
                    sameSource++;
                    continue;
                }
                ArticleCandidate candidate = wave.get(i);
                Article saved = candidate.toArticle(serviceDate);
                saved.markCleaned(article.sourceUrl(), readingTimeCalculator.calculate(article.cleanedContent()));
                collected.add(new CollectedArticle(saved, article.cleanedContent()));
                pickedStories.add(candidate.story());
                remaining.merge(candidate.categoryId(), -1, Integer::sum);
            }
        }

        private boolean isPickedStory(Story story) {
            return pickedStories.stream().anyMatch(picked -> storyMatcher.isSameStory(picked, story));
        }
    }

    private Optional<CrawledArticle> await(Future<Optional<CrawledArticle>> result) {
        try {
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ArticleErrorCode.ARTICLE_CRAWL_FAILED, "기사 크롤링이 중단되었습니다.");
        } catch (ExecutionException e) {
            log.warn("[ArticleCollect] 크롤링 작업 실패 - {}", e.getCause().getMessage());
            return Optional.empty();
        }
    }
}
