package com.byarrival.domain.user.web;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.user.exception.UserErrorCode;
import com.byarrival.domain.user.service.UserRestrictionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 이용 제한 중인 회원(MEMBER-03)은 로그인은 되지만 안내 화면에 필요한 API만 쓸 수 있다. 나머지 앱 API는 403
 * USER_RESTRICTED로 거절한다. 안내 화면은 내 정보(GET /api/v1/users/me)의 restriction으로 사유를 보여 준다.
 * 인증 API(/api/v1/auth/**)는 등록할 때 제외한다.
 */
@Component
@RequiredArgsConstructor
public class UserRestrictionInterceptor implements HandlerInterceptor {

    // 안내 화면 표시, 탈퇴, 이 기기의 푸시 끊기
    private static final Set<String> ALLOWED = Set.of(
        "GET /api/v1/users/me",
        "DELETE /api/v1/users/me",
        "DELETE /api/v1/devices");

    private final UserRestrictionService userRestrictionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = loginUserId();
        if (userId == null || isAllowed(request)) {
            return true;
        }
        if (userRestrictionService.isRestricted(userId)) {
            throw new BusinessException(UserErrorCode.USER_RESTRICTED);
        }
        return true;
    }

    private static boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ALLOWED.contains(request.getMethod() + " " + path);
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
