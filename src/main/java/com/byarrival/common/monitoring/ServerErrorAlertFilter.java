package com.byarrival.common.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 컨트롤러 밖(필터 등)에서 난 예외도 서버 에러 채널에 알린다.
 * 컨트롤러 예외는 GlobalExceptionHandler가 응답으로 바꾸므로 여기까지 올라오지 않아 중복 알림이 없다.
 * 인증 실패 같은 4xx는 SecurityErrorHandler가 응답으로 바꾸므로 예외로 올라오지 않는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ServerErrorAlertFilter extends OncePerRequestFilter {

    private final ServerErrorNotifier serverErrorNotifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | RuntimeException e) {
            // IOException은 대부분 클라이언트가 연결을 끊은 경우라 알리지 않는다
            serverErrorNotifier.notify(request.getMethod(), request.getRequestURI(), rootCause(e));
            throw e;
        }
    }

    // ServletException으로 감싸진 경우 실제 원인을 알린다
    private static Throwable rootCause(Throwable error) {
        if (error instanceof ServletException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}
