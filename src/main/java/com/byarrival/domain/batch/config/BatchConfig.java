package com.byarrival.domain.batch.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 운영자 새로고침 배치를 요청과 분리해 실행하는 설정
 */
@Configuration
public class BatchConfig {

    public static final String REFRESH_EXECUTOR = "batchRefreshExecutor";

    // 같은 서비스일 배치는 batch_run 제약으로 하나만 돌므로 스레드 하나면 된다
    @Bean(name = REFRESH_EXECUTOR)
    public Executor batchRefreshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("batch-refresh-");
        executor.initialize();
        return executor;
    }
}
