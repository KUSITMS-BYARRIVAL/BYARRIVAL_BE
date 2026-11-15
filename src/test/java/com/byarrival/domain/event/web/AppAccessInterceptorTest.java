package com.byarrival.domain.event.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.event.service.UsageEventRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 앱 접속 기록(LOG-01). 날짜는 서비스일(04:00)이 아니라 KST 자정 기준이고, 같은 날 두 번째 요청부터는 DB에 가지 않는다.
 */
class AppAccessInterceptorTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private UsageEventRecorder recorder;
    private MutableClock clock;
    private AppAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        recorder = mock(UsageEventRecorder.class);
        given(recorder.recordAppAccess(anyLong(), any(), any())).willReturn(true);
        clock = new MutableClock(LocalDateTime.of(2026, 11, 12, 8, 0));
        interceptor = new AppAccessInterceptor(recorder, clock);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 같은_날_여러_번_불러도_한_번만_저장한다() {
        request(1L);
        request(1L);

        verify(recorder, times(1)).recordAppAccess(eq(1L), eq(LocalDate.of(2026, 11, 12)), any());
    }

    @Test
    void 자정부터_새벽_4시_전_접속도_그날_날짜로_남긴다() {
        clock.set(LocalDateTime.of(2026, 11, 12, 3, 59));

        request(1L);

        // 서비스일은 11-11이지만 앱 접속은 KST 날짜인 11-12다
        verify(recorder).recordAppAccess(eq(1L), eq(LocalDate.of(2026, 11, 12)), any());
    }

    @Test
    void 날짜가_바뀌면_어제_기록을_비우고_다시_저장한다() {
        request(1L);
        request(2L);
        assertThat(interceptor.cachedUserCount()).isEqualTo(2);

        clock.set(LocalDateTime.of(2026, 11, 13, 0, 1));
        request(1L);

        verify(recorder).recordAppAccess(eq(1L), eq(LocalDate.of(2026, 11, 13)), any());
        assertThat(interceptor.cachedUserCount()).isEqualTo(1);
    }

    @Test
    void 저장에_실패하면_다음_요청에서_다시_시도한다() {
        given(recorder.recordAppAccess(eq(1L), any(), any())).willReturn(false, true);

        request(1L);
        request(1L);
        request(1L);

        verify(recorder, times(2)).recordAppAccess(eq(1L), eq(LocalDate.of(2026, 11, 12)), any());
    }

    @Test
    void 로그인하지_않은_요청은_남기지_않는다() {
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        verify(recorder, never()).recordAppAccess(anyLong(), any(), any());
    }

    private void request(Long userId) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(String.valueOf(userId)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(LocalDateTime now) {
            set(now);
        }

        void set(LocalDateTime now) {
            instant = now.atZone(KST).toInstant();
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
            return instant;
        }
    }
}
