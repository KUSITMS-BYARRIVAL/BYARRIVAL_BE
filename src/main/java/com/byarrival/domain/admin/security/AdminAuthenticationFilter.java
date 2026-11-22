package com.byarrival.domain.admin.security;

import com.byarrival.common.security.SecurityErrorHandler;
import com.byarrival.domain.admin.service.AdminAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 어드민 쿠키로 인증한다. 앱 회원 토큰만 들고 오면 403으로 막는다.
 * 서블릿 필터로 전역 등록되지 않도록 빈으로 만들지 않고 어드민 보안 설정에서 생성한다.
 */
@RequiredArgsConstructor
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminAuthService adminAuthService;
    private final JwtDecoder appTokenDecoder;
    private final SecurityErrorHandler errorHandler;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
            || (HttpMethod.POST.matches(request.getMethod())
            && AdminSecurityConfig.LOGIN_PATH.equals(request.getServletPath() + nullToEmpty(request.getPathInfo())));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String token = cookieToken(request);
        if (token == null) {
            if (hasAppToken(request)) {
                errorHandler.handle(request, response, new AccessDeniedException("앱 토큰으로는 어드민에 접근할 수 없습니다."));
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        AdminPrincipal principal;
        try {
            principal = adminAuthService.authenticate(token);
        } catch (JwtException | NumberFormatException e) {
            errorHandler.commence(request, response, new InvalidBearerTokenException("유효하지 않은 어드민 토큰입니다.", e));
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private boolean hasAppToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }
        try {
            appTokenDecoder.decode(header.substring(BEARER_PREFIX.length()));
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    private static String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
            .filter(cookie -> AdminCookieFactory.COOKIE_NAME.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
