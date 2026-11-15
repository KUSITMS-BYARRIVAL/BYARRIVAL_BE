package com.byarrival.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.byarrival.domain.home.config.HomeProperties;
import com.byarrival.domain.home.dto.response.HomeResponse;
import com.byarrival.domain.route.dto.response.PopularArticlesResponse;
import com.byarrival.domain.route.dto.response.TodayRouteResponse;
import com.byarrival.domain.route.service.PopularArticleService;
import com.byarrival.domain.route.service.PopularArticleService.PopularArticles;
import com.byarrival.domain.route.service.RouteService;
import com.byarrival.domain.route.service.RouteStreakService;
import com.byarrival.domain.smalltalk.dto.response.SmalltalkListResponse;
import com.byarrival.domain.smalltalk.dto.response.SmalltalkResponse;
import com.byarrival.domain.smalltalk.service.SmalltalkService;
import com.byarrival.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeServiceTest {

    private static final Long USER_ID = 1L;
    // 03:00은 아직 전날 서비스일이다
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 15);

    private RouteService routeService;
    private RouteStreakService routeStreakService;
    private SmalltalkService smalltalkService;
    private PopularArticleService popularArticleService;
    private UserService userService;
    private TodayVisitorStore todayVisitorStore;
    private HomeService homeService;

    @BeforeEach
    void setUp() {
        routeService = mock(RouteService.class);
        routeStreakService = mock(RouteStreakService.class);
        smalltalkService = mock(SmalltalkService.class);
        popularArticleService = mock(PopularArticleService.class);
        userService = mock(UserService.class);
        todayVisitorStore = mock(TodayVisitorStore.class);
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 11, 16, 3, 0, 0, 0, kst).toInstant(), kst);
        homeService = new HomeService(routeService, routeStreakService, smalltalkService, popularArticleService,
            userService, todayVisitorStore, new HomeProperties(3, 3, 10), clock);

        given(routeService.getToday(USER_ID)).willReturn(TodayRouteResponse.none());
        given(routeStreakService.streakDays(USER_ID, SERVICE_DATE)).willReturn(4);
        given(smalltalkService.getToday(USER_ID)).willReturn(new SmalltalkListResponse(SERVICE_DATE, smalltalks(5)));
        given(todayVisitorStore.count(SERVICE_DATE)).willReturn(OptionalLong.of(12));
        given(userService.findJobGroups(USER_ID)).willReturn(List.of());
        given(popularArticleService.mostCarted(eq(SERVICE_DATE), any(), any(), anyInt()))
            .willReturn(new PopularArticles(false, List.of()));
        given(popularArticleService.toResponse(any())).willReturn(PopularArticlesResponse.aggregating());
    }

    @Test
    void 서비스일_기준으로_영역을_모으고_스몰토크는_3개까지만_돌려준다() {
        HomeResponse response = homeService.getHome(USER_ID);

        assertThat(response.serviceDate()).isEqualTo(SERVICE_DATE);
        assertThat(response.streakDays()).isEqualTo(4);
        assertThat(response.smalltalks()).hasSize(3);
        assertThat(response.todayVisitorCount()).isEqualTo(12L);
        assertThat(response.popular()).hasSize(1);
    }

    @Test
    void 한_영역이_실패하면_그_영역만_null이고_나머지는_돌려준다() {
        given(routeService.getToday(USER_ID)).willThrow(new IllegalStateException("DB 오류"));
        given(todayVisitorStore.count(SERVICE_DATE)).willReturn(OptionalLong.empty());

        HomeResponse response = homeService.getHome(USER_ID);

        assertThat(response.todayRoute()).isNull();
        assertThat(response.todayVisitorCount()).isNull();
        assertThat(response.streakDays()).isEqualTo(4);
        assertThat(response.smalltalks()).hasSize(3);
        assertThat(response.popular()).isNotNull();
    }

    private static List<SmalltalkResponse> smalltalks(int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(i -> new SmalltalkResponse((long) i, "한 줄 " + i, 1L, "경제·금융", (long) i, "기사", "https://a",
                false))
            .toList();
    }
}
