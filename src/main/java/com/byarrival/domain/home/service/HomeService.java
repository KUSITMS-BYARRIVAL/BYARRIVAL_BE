package com.byarrival.domain.home.service;

import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.home.config.HomeProperties;
import com.byarrival.domain.home.dto.response.HomeResponse;
import com.byarrival.domain.home.dto.response.PopularChipResponse;
import com.byarrival.domain.route.dto.response.TodayRouteResponse;
import com.byarrival.domain.route.service.PopularArticleService;
import com.byarrival.domain.route.service.RouteService;
import com.byarrival.domain.route.service.RouteStreakService;
import com.byarrival.domain.smalltalk.dto.response.SmalltalkResponse;
import com.byarrival.domain.smalltalk.service.SmalltalkService;
import com.byarrival.domain.user.entity.JobGroup;
import com.byarrival.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 홈 화면을 영역별로 모아 돌려준다. 영역마다 따로 조회해 한 영역이 실패해도 나머지는 보여 준다(HOME-01 영역 단위 오류).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    private final RouteService routeService;
    private final RouteStreakService routeStreakService;
    private final SmalltalkService smalltalkService;
    private final PopularArticleService popularArticleService;
    private final UserService userService;
    private final TodayVisitorStore todayVisitorStore;
    private final HomeProperties properties;
    private final Clock clock;

    public HomeResponse getHome(Long userId) {
        LocalDate today = ServiceDay.today(clock);
        TodayRouteResponse todayRoute = section("todayRoute", () -> routeService.getToday(userId));
        Integer streakDays = section("streak", () -> routeStreakService.streakDays(userId, today));
        List<SmalltalkResponse> smalltalks = section("smalltalk", () -> smalltalkService.getToday(userId)
            .smalltalks().stream()
            .limit(properties.smalltalkPreviewSize())
            .toList());
        Long visitorCount = todayVisitorStore.count(today).stream().boxed().findFirst().orElse(null);
        List<PopularChipResponse> popular = section("popular",
            () -> popularChips(userId, today, properties.popularPreviewSize()));
        return new HomeResponse(today, todayRoute, streakDays, smalltalks, visitorCount, popular);
    }

    /**
     * 출근길 공감대 전체보기(HOME-06). jobGroup이 null이면 전체 칩이다.
     */
    public PopularChipResponse getPopular(JobGroup jobGroup) {
        LocalDate today = ServiceDay.today(clock);
        return chip(today, jobGroup, properties.popularDetailSize());
    }

    // 내가 고른 직군 칩을 고른 순서대로, 그다음 전체 칩
    private List<PopularChipResponse> popularChips(Long userId, LocalDate today, int size) {
        List<PopularChipResponse> chips = new ArrayList<>();
        for (JobGroup jobGroup : userService.findJobGroups(userId)) {
            chips.add(chip(today, jobGroup, size));
        }
        chips.add(chip(today, null, size));
        return chips;
    }

    private PopularChipResponse chip(LocalDate today, JobGroup jobGroup, int size) {
        return PopularChipResponse.of(jobGroup, popularArticleService.toResponse(
            popularArticleService.mostCarted(today, jobGroup, Set.of(), size)));
    }

    private <T> T section(String name, Supplier<T> query) {
        try {
            return query.get();
        } catch (RuntimeException e) {
            log.warn("[Home] {} 영역 조회 실패", name, e);
            return null;
        }
    }
}
