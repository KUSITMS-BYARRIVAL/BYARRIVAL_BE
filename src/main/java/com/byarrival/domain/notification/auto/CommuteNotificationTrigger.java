package com.byarrival.domain.notification.auto;

import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.notification.auto.NotificationMessages.PopularScope;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.route.entity.Route;
import com.byarrival.domain.route.entity.RouteStatus;
import com.byarrival.domain.route.repository.CartedArticleCount;
import com.byarrival.domain.route.service.RouteNotificationQueryService;
import com.byarrival.domain.route.service.RouteStreakService;
import com.byarrival.domain.user.entity.JobGroup;
import com.byarrival.domain.user.service.UserNotificationTargetService;
import com.byarrival.domain.user.service.UserNotificationTargetService.Profile;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 출근 시각 기준 알림: 오늘의 노선 준비(출근 전), 직군·지역 인기 기사(출근 후), 스트릭 이탈 리마인드(출근 후).
 * 1분마다 불러 지금 알림 시각이 된 회원에게 보낸다. 그 요일을 쉬는 날로 둔 회원은 출근 시각이 없어 대상이 아니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommuteNotificationTrigger {

    static final String ARTICLE_ID_KEY = "articleId";
    static final String URL_KEY = "url";

    private final AutoNotificationProperties properties;
    private final UserNotificationTargetService targetService;
    private final RouteNotificationQueryService routeQueryService;
    private final RouteStreakService routeStreakService;
    private final ArticleQueryService articleQueryService;
    private final NotificationMessages messages;
    private final AutoNotificationSender sender;

    public void run(LocalDateTime now) {
        LocalDate today = ServiceDay.of(now);
        forEach(commuters(now, -properties.routeReadyBeforeMinutes()), userId -> sendRouteReady(userId, today));
        Map<PopularKey, List<CartedArticleCount>> popularCache = new HashMap<>();
        forEach(commuters(now, properties.popularAfterMinutes()), userId -> sendPopular(userId, today, popularCache));
        forEach(commuters(now, properties.streakBreakAfterMinutes()), userId -> sendStreakBreak(userId, today));
    }

    /**
     * 출근 전이면 보낸다. 출발했거나 완주했으면 보내지 않는다.
     */
    void sendRouteReady(Long userId, LocalDate today) {
        if (sender.isSentToday(userId, NotificationType.ROUTE_READY)) {
            return;
        }
        Optional<Route> route = routeQueryService.findRoute(userId, today);
        if (route.isPresent() && (route.get().getStartedAt() != null
            || route.get().getStatus() == RouteStatus.COMPLETED)) {
            return;
        }
        sender.send(userId, NotificationType.ROUTE_READY, messages.routeReadyTitle(),
            NotificationMessages.ROUTE_READY_BODY, Map.of());
    }

    /**
     * 평일 기준 연속 완주가 정해진 일수 이상인데 오늘 아직 완주하지 않았으면 보낸다.
     * 스트릭은 평일만 세서 주말에는 완주하지 않아도 끊기지 않으므로 주말에는 보내지 않는다.
     */
    void sendStreakBreak(Long userId, LocalDate today) {
        if (isWeekend(today) || sender.isSentToday(userId, NotificationType.STREAK_BREAK)) {
            return;
        }
        Optional<Route> route = routeQueryService.findRoute(userId, today);
        if (route.isPresent() && route.get().getStatus() == RouteStatus.COMPLETED) {
            return;
        }
        int streakDays = routeStreakService.streakDays(userId, today);
        if (streakDays < properties.streakBreakMinDays()) {
            return;
        }
        sender.send(userId, NotificationType.STREAK_BREAK, messages.streakBreakTitle(streakDays),
            NotificationMessages.STREAK_BREAK_BODY, Map.of());
    }

    /**
     * 근무 지역+직군 → 직군 → 근무 지역 순으로, 앞 범위에 인기 기사가 없을 때만 다음 범위로 넓힌다.
     * 인기 기사가 있는 범위를 찾으면 그 순위 안에서 아직 읽지 않은 첫 기사를 보내고, 모두 읽었으면 보내지 않는다.
     */
    void sendPopular(Long userId, LocalDate today, Map<PopularKey, List<CartedArticleCount>> cache) {
        if (sender.isSentToday(userId, NotificationType.POPULAR_ARTICLE)) {
            return;
        }
        Optional<Profile> profile = targetService.findProfile(userId);
        if (profile.isEmpty()) {
            return;
        }
        for (PopularScope scope : scopesOf(profile.get())) {
            PopularKey key = PopularKey.of(scope, profile.get());
            List<CartedArticleCount> popular = cache.computeIfAbsent(key, k -> routeQueryService.findPopular(today,
                k.sigunguId(), k.jobGroup(), properties.popularMinUsers(), properties.popularTopSize()));
            if (popular.isEmpty()) {
                continue;
            }
            sendFirstUnread(userId, popular, scope, profile.get());
            return;
        }
    }

    private void sendFirstUnread(Long userId, List<CartedArticleCount> popular, PopularScope scope, Profile profile) {
        List<Long> articleIds = popular.stream().map(CartedArticleCount::getArticleId).toList();
        Set<Long> read = routeQueryService.findReadArticleIds(userId, articleIds);
        Optional<Long> target = articleIds.stream().filter(id -> !read.contains(id)).findFirst();
        if (target.isEmpty()) {
            return;
        }
        Article article = articleQueryService.findAllByIds(List.of(target.get())).get(target.get());
        if (article == null) {
            return;
        }
        String jobGroupName = profile.jobGroup() == null ? null : profile.jobGroup().getDisplayName();
        sender.send(userId, NotificationType.POPULAR_ARTICLE, messages.popularTitle(article.getTitle()),
            messages.popularBody(scope, profile.sigunguName(), jobGroupName),
            Map.of(ARTICLE_ID_KEY, String.valueOf(article.getId()), URL_KEY, article.linkUrl()));
    }

    private static List<PopularScope> scopesOf(Profile profile) {
        List<PopularScope> scopes = new ArrayList<>();
        boolean hasRegion = profile.sigunguId() != null;
        boolean hasJob = profile.jobGroup() != null;
        if (hasRegion && hasJob) {
            scopes.add(PopularScope.REGION_AND_JOB);
        }
        if (hasJob) {
            scopes.add(PopularScope.JOB);
        }
        if (hasRegion) {
            scopes.add(PopularScope.REGION);
        }
        return scopes;
    }

    private List<Long> commuters(LocalDateTime now, int offsetMinutes) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (CommuteWindow window : CommuteWindow.of(now, offsetMinutes, properties.graceMinutes())) {
            userIds.addAll(targetService.findCommuters(window.day(), window.from(), window.to()));
        }
        return List.copyOf(userIds);
    }

    // 한 회원에서 실패해도 다른 회원에게는 계속 보낸다
    private static void forEach(List<Long> userIds, Consumer<Long> action) {
        for (Long userId : userIds) {
            try {
                action.accept(userId);
            } catch (RuntimeException e) {
                log.error("[AutoNotification] 출근 시각 알림 처리 실패: userId={}", userId, e);
            }
        }
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /**
     * 인기 기사 집계 범위. 같은 작업 안에서 같은 범위는 한 번만 집계한다.
     */
    record PopularKey(Integer sigunguId, JobGroup jobGroup) {

        static PopularKey of(PopularScope scope, Profile profile) {
            return switch (scope) {
                case REGION_AND_JOB -> new PopularKey(profile.sigunguId(), profile.jobGroup());
                case JOB -> new PopularKey(null, profile.jobGroup());
                case REGION -> new PopularKey(profile.sigunguId(), null);
            };
        }
    }
}
