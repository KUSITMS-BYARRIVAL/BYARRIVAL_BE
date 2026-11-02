package com.byarrival.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 일일 배치 등 @Scheduled 작업 활성화
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
