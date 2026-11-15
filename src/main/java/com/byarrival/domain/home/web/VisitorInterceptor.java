package com.byarrival.domain.home.web;

import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.home.service.TodayVisitorStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인한 회원이 API를 부르면 그 서비스일(04:00 경계) 접속자로 남긴다(HOME-04). 회원 단위라 여러 기기·여러 번 불러도 한 명이다.
 * 같은 회원은 이 서버에서 1분에 한 번만 Redis에 쓴다. 오늘 접속자 수에는 첫 기록만 있으면 되고,
 * 나중에 실시간 접속자로 바꿀 때는 1분 단위로 갱신되는 마지막 접속 시각을 쓴다.
 */
@Component
public class VisitorInterceptor implements HandlerInterceptor {

    static final Duration RECORD_INTERVAL = Duration.ofMinutes(1);
    static final int MAX_CACHED_USERS = 100_000;

    private final TodayVisitorStore todayVisitorStore;
    private final Clock clock;
    private final Map<Long, Instant> lastRecorded = new ConcurrentHashMap<>();
    private volatile LocalDate cachedServiceDate;

    public VisitorInterceptor(TodayVisitorStore todayVisitorStore, Clock clock) {
        this.todayVisitorStore = todayVisitorStore;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = loginUserId();
        if (userId == null) {
            return true;
        }
        Instant now = clock.instant();
        LocalDate serviceDate = ServiceDay.today(clock);
        resetIfStale(serviceDate);
        Instant last = lastRecorded.get(userId);
        if (last == null || !now.isBefore(last.plus(RECORD_INTERVAL))) {
            // 저장에 실패하면 기록하지 않은 것으로 두어 다음 요청에서 다시 시도한다
            if (todayVisitorStore.record(userId, serviceDate, now)) {
                lastRecorded.put(userId, now);
            }
        }
        return true;
    }

    int cachedUserCount() {
        return lastRecorded.size();
    }

    // 서비스일이 바뀌면 새 키에 다시 기록해야 하므로 비운다. 너무 커져도 비운다
    private void resetIfStale(LocalDate serviceDate) {
        if (!serviceDate.equals(cachedServiceDate) || lastRecorded.size() >= MAX_CACHED_USERS) {
            synchronized (this) {
                if (!serviceDate.equals(cachedServiceDate) || lastRecorded.size() >= MAX_CACHED_USERS) {
                    lastRecorded.clear();
                    cachedServiceDate = serviceDate;
                }
            }
        }
    }

    private static Long loginUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
