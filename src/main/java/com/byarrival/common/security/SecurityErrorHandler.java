package com.byarrival.common.security;

import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.exception.ErrorCode;
import com.byarrival.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증·인가 실패를 공통 응답 형식으로 돌려준다.
 * 토큰 만료는 TOKEN_EXPIRED, 잘못된 토큰은 INVALID_TOKEN, 토큰 없음은 UNAUTHORIZED로 구분한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
        AuthenticationException authException) throws IOException {
        ErrorCode errorCode = resolve(authException);
        log.warn("[{}] {} {}", errorCode.name(), request.getMethod(), request.getRequestURI());
        write(response, errorCode);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
        AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[FORBIDDEN] {} {}", request.getMethod(), request.getRequestURI());
        write(response, CommonErrorCode.FORBIDDEN);
    }

    private ErrorCode resolve(AuthenticationException exception) {
        if (!(exception instanceof InvalidBearerTokenException)) {
            return CommonErrorCode.UNAUTHORIZED;
        }
        if (exception.getCause() instanceof JwtValidationException validationException
            && validationException.getErrors().stream()
            .anyMatch(error -> JwtExpiryValidator.TOKEN_EXPIRED.equals(error.getErrorCode()))) {
            return CommonErrorCode.TOKEN_EXPIRED;
        }
        return CommonErrorCode.INVALID_TOKEN;
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.fail(errorCode));
    }
}
