package com.byarrival.domain.event.web;

import com.byarrival.domain.event.service.UsageEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인한 사용자가 API를 부르면 그날(KST 자정 기준) 앱 접속으로 남긴다. 앱을 열면 홈·노선 조회 같은 API를 부르므로
 * 프론트가 따로 접속 API를 부르지 않아도 된다. 같은 날 두 번째 요청부터는 메모리에서 걸러 DB에 가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AppAccessInterceptor implements HandlerInterceptor {

    private final UsageEventRecorder usageEventRecorder;
    private final Clock clock;
    private final Map<Long, LocalDate> recordedDates = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = loginUserId();
        if (userId == null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        // 저장에 실패하면 기록하지 않은 것으로 두어 다음 요청에서 다시 시도한다
        if (!today.equals(recordedDates.get(userId)) && usageEventRecorder.recordAppAccess(userId, today, now)) {
            recordedDates.put(userId, today);
        }
        return true;
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
