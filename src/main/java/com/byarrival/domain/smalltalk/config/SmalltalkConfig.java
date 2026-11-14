package com.byarrival.domain.smalltalk.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SmalltalkProperties.class)
public class SmalltalkConfig {

    // 카테고리마다 따로 만든다. 카테고리 안에서는 인기 순서대로 대체해야 해서 한 줄로 처리한다
    private static final int CATEGORY_CONCURRENCY = 8;

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService smalltalkGenerationExecutor() {
        return Executors.newFixedThreadPool(CATEGORY_CONCURRENCY, Thread.ofPlatform().name("smalltalk-gen-", 0).factory());
    }
}
