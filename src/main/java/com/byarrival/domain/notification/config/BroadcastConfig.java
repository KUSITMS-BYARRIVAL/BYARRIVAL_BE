package com.byarrival.domain.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BroadcastProperties.class)
public class BroadcastConfig {
}
