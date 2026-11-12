package com.byarrival.domain.route.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.event.service.UsageEventRecorder;
import com.byarrival.domain.route.dto.response.ArrivalResponse;
import com.byarrival.domain.route.dto.response.RideProgressResponse;
import com.byarrival.domain.route.dto.response.RideResponse;
import com.byarrival.domain.route.dto.response.RouteStopResponse;
import com.byarrival.domain.route.dto.response.StopReadResponse;
import com.byarrival.domain.route.entity.Route;
import com.byarrival.domain.route.entity.RouteCard;
import com.byarrival.domain.route.entity.RouteStatus;
import com.byarrival.domain.route.exception.RouteErrorCode;
import com.byarrival.domain.route.repository.RouteRepository;
import com.byarrival.domain.route.service.RouteCommandService.ArriveOutcome;
import com.byarrival.domain.route.service.RouteCommandService.ReadOutcome;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노선 운행: 출발하기, 정거장 기사 조회, 완독, 도착하기.
 * 노선 상태 변경과 이벤트 로그 저장을 서로 다른 트랜잭션으로 나누기 위해 명령 메서드는 트랜잭션을 직접 열지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RouteRideService {

    private final RouteRepository routeRepository;
    private final RouteCommandService routeCommandService;
    private final RideProgressReader rideProgressReader;
    private final RouteStreakService routeStreakService;
    private final UsageEventRecorder usageEventRecorder;
    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;
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
                null, null, null, null, null, null, null, null, null, null, card.isRead(), progressResponse);
        }
        Article article = articleQueryService.findAllByIds(List.of(card.getArticleId())).get(card.getArticleId());
        String categoryName = categoryMappingService.getActiveCategoryNames().get(article.getCategoryId());
        List<String> summary = Stream.of(article.getSummarySentence1(), article.getSummarySentence2(),
                article.getSummarySentence3())
            .filter(sentence -> sentence != null)
            .toList();
        String url = article.getSourceUrl() != null ? article.getSourceUrl() : article.getOriginalUrl();
        return new RouteStopResponse(routeId, stopNumber, progress.totalStops(), true, article.getId(),
            article.getTitle(), article.getPressName(), article.getPublishedAt(), article.getCategoryId(), categoryName,
            card.getReadingMinutes().setScale(0, RoundingMode.CEILING).intValue(),
            article.getSummaryKeywords() == null ? List.of() : article.getSummaryKeywords(),
            article.getSummaryOneLine(), summary, url, card.isRead(), progressResponse);
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
     * 도착하기: 완주로 기록하고 완주 결과(읽은 기사 수, 연속 이용, 이용시간)를 돌려준다.
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
            route.getCompletedAt());
    }
}
