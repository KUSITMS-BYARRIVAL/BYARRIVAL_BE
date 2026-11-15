package com.byarrival.domain.batch.service;

import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.article.service.ArticleAiService;
import com.byarrival.domain.article.service.ArticleStoryService;
import com.byarrival.domain.article.service.ArticleCollectService;
import com.byarrival.domain.article.service.ArticleCollectService.CategoryFill;
import com.byarrival.domain.feed.dto.FeedCollectResult;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.service.FeedCollectService;
import com.byarrival.domain.quiz.service.QuizGenerationService;
import com.byarrival.domain.smalltalk.service.SmalltalkGenerationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 매일 23:45 일일 배치. 다음 서비스일에 노출할 기사를 수집하고, 카테고리별로 고른 기사의 원문을 크롤링해 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyBatchService {

    private final BatchRunService batchRunService;
    private final FeedCollectService feedCollectService;
    private final ArticleCollectService articleCollectService;
    private final ArticleAiService articleAiService;
    private final ArticleStoryService articleStoryService;
    private final QuizGenerationService quizGenerationService;
    private final SmalltalkGenerationService smalltalkGenerationService;
    private final BatchMonitor batchMonitor;
    private final Clock clock;

    public void run() {
        // 23:45에 수집한 기사는 다음 04:00부터 시작하는 서비스일에 노출된다
        LocalDate serviceDate = ServiceDay.today(clock).plusDays(1);
        Optional<Long> batchRunId = batchRunService.start(serviceDate);
        if (batchRunId.isEmpty()) {
            log.info("[DailyBatch] serviceDate={} 배치가 이미 실행 중이거나 완료되어 건너뜀", serviceDate);
            return;
        }
        Instant startedAt = batchMonitor.start();

        try {
            FeedCollectResult collectResult = feedCollectService.collect(serviceDate);
            if (collectResult.allFailed()) {
                // 다시 실행할 수 있도록 실패로 남긴다. 실패 횟수는 서비스일당 1회만 세므로 재실행해도 늘지 않는다.
                fail(batchRunId.get(), serviceDate, startedAt,
                    "모든 Feed 수집 실패 feedIds=" + collectResult.failedFeedIds());
                return;
            }
            List<FetchedFeed> fetchedFeeds = collectResult.fetchedFeeds();
            List<Long> saveFailedFeedIds = saveArticles(fetchedFeeds, serviceDate);
            // 난이도 판정과 3줄 요약(AI-01, AI-02). 일부 Feed·카테고리가 실패해도 저장된 기사는 먼저 가공해
            // 나머지 카테고리라도 추천할 수 있게 한다. 요약까지 끝난 기사만 추천 후보가 된다(RSS-08)
            articleAiService.process(serviceDate);
            // 표현이 달라 수집 때 못 거른 같은 사건 기사를 AI로 묶어 1건만 추천 후보로 남긴다. 실패하면 건너뛴다
            articleStoryService.dedupe(serviceDate);
            // 추천 후보로 남은 기사마다 3지선다 퀴즈 1문제를 만든다(AI-03). 실패한 기사는 퀴즈 없이 노출한다
            generateQuizzes(serviceDate);
            // 카테고리별 인기 기사로 스몰토크를 최대 2건씩 만든다(TALK-01). 실패해도 기사 추천은 계속한다
            generateSmalltalks(serviceDate);
            if (!saveFailedFeedIds.isEmpty()) {
                // 다시 실행할 수 있도록 실패로 남긴다. 이미 저장·가공된 기사는 재실행 때 건너뛴다.
                fail(batchRunId.get(), serviceDate, startedAt, "기사 저장 실패 feedIds=" + saveFailedFeedIds);
                return;
            }
            CategoryFill fill = articleCollectService.checkCategoryFill(serviceDate);
            if (!fill.shortage().isEmpty()) {
                log.warn("[DailyBatch] serviceDate={} 최대 수를 채우지 못한 카테고리 categoryId=기사 수 {}",
                    serviceDate, fill.shortage());
            }
            if (!fill.emptyCategoryIds().isEmpty()) {
                // 그날 추천을 할 수 없는 카테고리. 다시 실행해 빈 카테고리를 채울 수 있도록 실패로 남긴다.
                String reason = "기사 0건 카테고리 categoryIds=" + fill.emptyCategoryIds();
                batchRunService.fail(batchRunId.get(), reason);
                batchMonitor.failed(serviceDate, startedAt, reason, fill);
                return;
            }
            CategoryFill ready = articleCollectService.checkRecommendableFill(serviceDate);
            if (!ready.emptyCategoryIds().isEmpty()) {
                // 요약을 하나도 못 해 그날 추천을 할 수 없는 카테고리. 다시 실행하면 남은 기사만 가공한다
                String reason = "추천 가능 기사 0건 카테고리 categoryIds=" + ready.emptyCategoryIds();
                batchRunService.fail(batchRunId.get(), reason);
                batchMonitor.failed(serviceDate, startedAt, reason, ready);
                return;
            }
            batchRunService.complete(batchRunId.get());
            batchMonitor.completed(serviceDate, startedAt, ready);
            log.info("[DailyBatch] serviceDate={} 완료 - 수집 Feed {}개", serviceDate, fetchedFeeds.size());
        } catch (RuntimeException e) {
            log.error("[DailyBatch] serviceDate={} 실패 - {}", serviceDate, e.getMessage(), e);
            batchRunService.fail(batchRunId.get(), e.getMessage());
            // 예외 메시지에는 외부 URL이나 사용자 값이 섞일 수 있어 알림에는 종류만 보낸다
            batchMonitor.failed(serviceDate, startedAt, e.getClass().getName());
        }
    }

    // 퀴즈가 실패해도 기사 추천은 할 수 있으므로 배치를 실패로 남기지 않는다
    private void generateQuizzes(LocalDate serviceDate) {
        try {
            quizGenerationService.generate(serviceDate);
        } catch (RuntimeException e) {
            log.error("[DailyBatch] serviceDate={} 퀴즈 생성 실패 - {}", serviceDate, e.getMessage(), e);
        }
    }

    private void generateSmalltalks(LocalDate serviceDate) {
        try {
            smalltalkGenerationService.generate(serviceDate);
        } catch (RuntimeException e) {
            log.error("[DailyBatch] serviceDate={} 스몰토크 생성 실패 - {}", serviceDate, e.getMessage(), e);
        }
    }

    private void fail(Long batchRunId, LocalDate serviceDate, Instant startedAt, String reason) {
        batchRunService.fail(batchRunId, reason);
        batchMonitor.failed(serviceDate, startedAt, reason);
    }

    // 한 Feed의 저장이 실패해도 나머지 Feed는 저장한다
    private List<Long> saveArticles(List<FetchedFeed> fetchedFeeds, LocalDate serviceDate) {
        List<Long> failedFeedIds = new ArrayList<>();
        int saved = 0;
        for (FetchedFeed feed : fetchedFeeds) {
            try {
                saved += articleCollectService.collect(feed, serviceDate);
            } catch (RuntimeException e) {
                failedFeedIds.add(feed.feedId());
                log.error("[DailyBatch] serviceDate={} feedId={} 기사 저장 실패 - {}",
                    serviceDate, feed.feedId(), e.getMessage(), e);
            }
        }
        log.info("[DailyBatch] serviceDate={} 기사 {}건 저장", serviceDate, saved);
        return failedFeedIds;
    }
}
