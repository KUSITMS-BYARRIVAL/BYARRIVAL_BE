package com.byarrival.domain.home.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.home.service.TodayVisitorStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class VisitorInterceptorTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 7L;

    private TodayVisitorStore store;
    private MutableClock clock;
    private VisitorInterceptor interceptor;

    @BeforeEach
    void setUp() {
        store = mock(TodayVisitorStore.class);
        given(store.record(any(), any(), any())).willReturn(true);
        clock = new MutableClock(ZonedDateTime.of(2026, 11, 16, 8, 0, 0, 0, KST).toInstant());
        interceptor = new VisitorInterceptor(store, clock);
        login(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void API를_부른_회원을_그_서비스일_접속자로_남긴다() {
        call();

        verify(store).record(USER_ID, LocalDate.of(2026, 11, 16), clock.instant());
    }

    @Test
    void 같은_회원은_1분_안에_다시_불러도_한_번만_쓰고_1분이_지나면_마지막_접속_시각을_갱신한다() {
        call();
        clock.advance(Duration.ofSeconds(59));
        call();
        clock.advance(Duration.ofSeconds(1));
        call();

        verify(store, times(2)).record(eq(USER_ID), any(), any());
    }

    @Test
    void 서비스일이_바뀌면_1분이_안_지나도_새_서비스일에_다시_남긴다() {
        clock.set(ZonedDateTime.of(2026, 11, 17, 3, 59, 50, 0, KST).toInstant());
        call();
        clock.advance(Duration.ofSeconds(20));
        call();

        verify(store).record(eq(USER_ID), eq(LocalDate.of(2026, 11, 16)), any());
        verify(store).record(eq(USER_ID), eq(LocalDate.of(2026, 11, 17)), any());
    }

    @Test
    void 저장에_실패하면_다음_요청에서_다시_시도한다() {
        given(store.record(any(), any(), any())).willReturn(false, true);

        call();
        call();

        verify(store, times(2)).record(eq(USER_ID), any(), any());
    }

    @Test
    void 로그인하지_않은_요청은_남기지_않는다() {
        SecurityContextHolder.clearContext();

        call();

        verify(store, never()).record(any(), any(), any());
    }

    private void call() {
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
    }

    private static void login(Long userId) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(String.valueOf(userId)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
