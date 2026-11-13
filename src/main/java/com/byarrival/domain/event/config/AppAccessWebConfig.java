package com.byarrival.domain.event.config;

import com.byarrival.domain.event.web.AppAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AppAccessWebConfig implements WebMvcConfigurer {

    private final AppAccessInterceptor appAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(appAccessInterceptor).addPathPatterns("/api/v1/**");
    }
}
