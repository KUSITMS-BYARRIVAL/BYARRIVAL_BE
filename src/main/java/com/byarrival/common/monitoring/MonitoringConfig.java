package com.byarrival.common.monitoring;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 운영 알림 설정 등록
 */
@Configuration
@EnableConfigurationProperties(MonitoringProperties.class)
public class MonitoringConfig {
}
