package com.byarrival.domain.user.config;

import com.byarrival.domain.user.web.UserRestrictionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class UserRestrictionWebConfig implements WebMvcConfigurer {

    private final UserRestrictionInterceptor userRestrictionInterceptor;

    // 접속 기록·접속자 수보다 먼저 막아, 막힌 요청은 접속으로 세지 않는다
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userRestrictionInterceptor)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns("/api/v1/auth/**")
            .order(Ordered.HIGHEST_PRECEDENCE);
    }
}
