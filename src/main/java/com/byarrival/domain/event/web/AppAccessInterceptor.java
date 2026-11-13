package com.byarrival.domain.event.web;

import com.byarrival.domain.event.service.UsageEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인한 사용자가 API를 부르면 그날(KST 자정 기준) 앱 접속으로 남긴다. 앱을 열면 홈·노선 조회 같은 API를 부르므로
 * 프론트가 따로 접속 API를 부르지 않아도 된다.
 * 같은 날 두 번째 요청부터는 메모리에서 걸러 DB에 가지 않는다. 이 기록은 오늘 것만 들고 있다가 날짜가 바뀌면 비우고,
 * 최대 크기를 넘어도 비운다. 비운 뒤 다시 저장해도 DB의 날짜별 유일 제약이 중복을 막는다.
 */
@Component
public class AppAccessInterceptor implements HandlerInterceptor {

    static final int MAX_CACHED_USERS = 100_000;

    private final UsageEventRecorder usageEventRecorder;
    private final Clock clock;
    private final Set<Long> recordedToday = ConcurrentHashMap.newKeySet();
    private volatile LocalDate cachedDate;

    public AppAccessInterceptor(UsageEventRecorder usageEventRecorder, Clock clock) {
        this.usageEventRecorder = usageEventRecorder;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = loginUserId();
        if (userId == null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        resetIfStale(today);
        // 저장에 실패하면 기록하지 않은 것으로 두어 다음 요청에서 다시 시도한다
        if (!recordedToday.contains(userId) && usageEventRecorder.recordAppAccess(userId, today, now)) {
            recordedToday.add(userId);
        }
        return true;
    }

    int cachedUserCount() {
        return recordedToday.size();
    }

    // 날짜가 바뀌었거나 너무 커졌으면 비운다
    private void resetIfStale(LocalDate today) {
        if (!today.equals(cachedDate) || recordedToday.size() >= MAX_CACHED_USERS) {
            synchronized (this) {
                if (!today.equals(cachedDate) || recordedToday.size() >= MAX_CACHED_USERS) {
                    recordedToday.clear();
                    cachedDate = today;
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
