package com.byarrival.domain.admin.security;

import com.byarrival.common.security.SecurityErrorHandler;
import com.byarrival.domain.admin.config.AdminAuthProperties;
import com.byarrival.domain.admin.service.AdminAuthService;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 어드민 웹 전용 보안 설정. /api/admin/** 는 앱 인증과 분리해 어드민 쿠키로만 접근한다.
 * CSRF 토큰 대신 SameSite=Lax 쿠키와 허용 Origin 검사로 다른 사이트의 요청을 막는다.
 */
@Configuration
@EnableConfigurationProperties(AdminAuthProperties.class)
public class AdminSecurityConfig {

    static final String LOGIN_PATH = "/api/admin/auth/login";

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, AdminAuthService adminAuthService,
        JwtDecoder jwtDecoder, SecurityErrorHandler errorHandler, AdminAuthProperties properties) throws Exception {
        return http
            .securityMatcher("/api/admin/**")
            .cors(cors -> cors.configurationSource(corsSource(properties.allowedOrigins())))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, LOGIN_PATH).permitAll()
                .anyRequest().hasAnyRole("OPERATOR", "EDITOR"))
            .addFilterBefore(new AdminAuthenticationFilter(adminAuthService, jwtDecoder, errorHandler),
                UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(errorHandler)
                .accessDeniedHandler(errorHandler))
            .build();
    }

    private static CorsConfigurationSource corsSource(List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", configuration);
        return source;
    }
}
