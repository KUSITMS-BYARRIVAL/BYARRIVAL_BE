package com.byarrival.domain.route.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.event.service.UsageEventRecorder;
import com.byarrival.domain.route.dto.response.ArrivalResponse;
import com.byarrival.domain.route.dto.response.PopularArticleResponse;
import com.byarrival.domain.route.dto.response.PopularArticlesResponse;
import com.byarrival.domain.route.dto.response.RideProgressResponse;
import com.byarrival.domain.route.dto.response.RideResponse;
import com.byarrival.domain.route.dto.response.RouteStopResponse;
import com.byarrival.domain.route.dto.response.StopReadResponse;
import com.byarrival.domain.route.entity.Route;
import com.byarrival.domain.route.entity.RouteCard;
import com.byarrival.domain.route.entity.RouteStatus;
import com.byarrival.domain.route.exception.RouteErrorCode;
import com.byarrival.domain.route.repository.CartedArticleCount;
import com.byarrival.domain.route.repository.RouteRepository;
import com.byarrival.domain.route.service.PopularArticleService.PopularArticles;
import com.byarrival.domain.route.service.RouteCommandService.ArriveOutcome;
import com.byarrival.domain.route.service.RouteCommandService.ReadOutcome;
import com.byarrival.domain.scrap.service.ScrapService;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노선 운행: 출발하기, 정거장 기사 조회, 완독, 도착하기.
 * 노선 상태 변경과 이벤트 로그 저장을 서로 다른 트랜잭션으로 나누기 위해 명령 메서드는 트랜잭션을 직접 열지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteRideService {

    private static final int POPULAR_COUNT = 3;

    private final RouteRepository routeRepository;
    private final RouteCommandService routeCommandService;
    private final RideProgressReader rideProgressReader;
    private final RouteStreakService routeStreakService;
    private final UsageEventRecorder usageEventRecorder;
    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;
    private final PopularArticleService popularArticleService;
    private final ScrapService scrapService;
    private final Clock clock;

    public RideResponse depart(Long userId, Long routeId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Route route = routeCommandService.depart(userId, routeId, now);
        return new RideResponse(route.getId(), route.statusAt(now),
            RideProgressResponse.from(rideProgressReader.read(route)));
    }

    /**
     * n번째 정거장 기사. 운행 중이거나 오늘 완주한 노선만 볼 수 있다(완주 후 다시 보기).
     */
    @Transactional(readOnly = true)
    public RouteStopResponse getStop(Long userId, Long routeId, int stopNumber) {
        LocalDateTime now = LocalDateTime.now(clock);
        Route route = routeRepository.findByIdAndUserId(routeId, userId)
            .orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_NOT_FOUND));
        if (route.isExpired(now)) {
            throw new BusinessException(RouteErrorCode.ROUTE_EXPIRED);
        }
        RouteStatus status = route.getStatus();
        if (status != RouteStatus.IN_PROGRESS && status != RouteStatus.COMPLETED) {
            throw new BusinessException(RouteErrorCode.ROUTE_INVALID_STATUS);
        }
        RideProgress progress = rideProgressReader.read(route);
        if (!progress.hasStop(stopNumber)) {
            throw new BusinessException(RouteErrorCode.ROUTE_STOP_NOT_FOUND);
        }
        RouteCard card = progress.stop(stopNumber);
        RideProgressResponse progressResponse = RideProgressResponse.from(progress);
        if (!progress.isAvailable(card)) {
            return new RouteStopResponse(routeId, stopNumber, progress.totalStops(), false, card.getArticleId(),
                null, null, null, null, null, null, null, null, null, null, card.isRead(), false, progressResponse);
        }
        Article article = articleQueryService.findAllByIds(List.of(card.getArticleId())).get(card.getArticleId());
        String categoryName = categoryMappingService.getActiveCategoryNames().get(article.getCategoryId());
        List<String> summary = Stream.of(article.getSummarySentence1(), article.getSummarySentence2(),
                article.getSummarySentence3())
            .filter(sentence -> sentence != null)
            .toList();
        return new RouteStopResponse(routeId, stopNumber, progress.totalStops(), true, article.getId(),
            article.getTitle(), article.getPressName(), article.getPublishedAt(), article.getCategoryId(), categoryName,
            card.getReadingMinutes().setScale(0, RoundingMode.CEILING).intValue(),
            article.getSummaryKeywords() == null ? List.of() : article.getSummaryKeywords(),
            article.getSummaryOneLine(), summary, urlOf(article), card.isRead(),
            scrapService.findScrappedArticleIds(userId, List.of(article.getId())).contains(article.getId()),
            progressResponse);
    }

    /**
     * [기사 보러가기]: 완독으로 기록하고 열람 이벤트를 남긴다. 원문 열기에 실패해도 완독이다.
     */
    public StopReadResponse read(Long userId, Long routeId, int stopNumber) {
        LocalDateTime now = LocalDateTime.now(clock);
        ReadOutcome outcome = routeCommandService.read(userId, routeId, stopNumber, now);
        Route route = outcome.route();
        if (outcome.newlyRead()) {
            usageEventRecorder.recordArticleRead(userId, route.getId(), outcome.articleId(), route.getServiceDate(), now);
        }
        return new StopReadResponse(stopNumber, outcome.articleId(), true,
            RideProgressResponse.from(rideProgressReader.read(route)));
    }

    /**
     * 도착하기: 완주로 기록하고 완주 결과(읽은 기사 수, 연속 이용, 이용시간, 많이 담은 뉴스 TOP 3)를 돌려준다.
     */
    public ArrivalResponse arrive(Long userId, Long routeId) {
        LocalDateTime now = LocalDateTime.now(clock);
        ArriveOutcome outcome = routeCommandService.arrive(userId, routeId, now);
        Route route = outcome.route();
        if (outcome.newlyCompleted()) {
            usageEventRecorder.recordRouteCompleted(userId, route.getId(), route.getServiceDate(), now);
        }
        RideProgress progress = rideProgressReader.read(route);
        int streak = routeStreakService.streakDays(userId, route.getServiceDate());
        long usedMinutes = route.getStartedAt() == null ? 0
            : (Duration.between(route.getStartedAt(), route.getCompletedAt()).toSeconds() + 59) / 60;
        return new ArrivalResponse(route.getId(), progress.readCount(), progress.totalStops(), streak, usedMinutes,
            route.getCompletedAt(), popularOf(route, progress));
    }

    // 그날 많이 담은 뉴스 중 이번 노선에서 읽은 기사를 뺀 TOP 3. 집계에 실패해도 완주 결과는 돌려준다(ARR-02)
    private PopularArticlesResponse popularOf(Route route, RideProgress progress) {
        Set<Long> readArticleIds = progress.stops().stream()
            .filter(RouteCard::isRead)
            .map(RouteCard::getArticleId)
            .collect(Collectors.toSet());
        try {
            PopularArticles popular = popularArticleService.mostCarted(route.getServiceDate(), readArticleIds,
                POPULAR_COUNT);
            if (!popular.ranked()) {
                return PopularArticlesResponse.aggregating();
            }
            Map<Long, Article> articles = articleQueryService.findAllByIds(
                popular.articles().stream().map(CartedArticleCount::getArticleId).toList());
            Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
            List<PopularArticleResponse> ranked = new ArrayList<>();
            for (CartedArticleCount count : popular.articles()) {
                Article article = articles.get(count.getArticleId());
                ranked.add(new PopularArticleResponse(ranked.size() + 1, article.getId(), article.getTitle(),
                    article.getPressName(), article.getCategoryId(), categoryNames.get(article.getCategoryId()),
                    count.getUserCount(), urlOf(article)));
            }
            return PopularArticlesResponse.ranked(ranked);
        } catch (RuntimeException e) {
            log.warn("[Route] routeId={} 많이 담은 뉴스 집계 실패", route.getId(), e);
            return null;
        }
    }

    // 크롤링한 원문 주소가 없으면 수집한 링크로 연다
    private static String urlOf(Article article) {
        return article.getSourceUrl() != null ? article.getSourceUrl() : article.getOriginalUrl();
    }
}
