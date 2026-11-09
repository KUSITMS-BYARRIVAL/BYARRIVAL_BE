package com.byarrival.domain.onboarding.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OnboardingProperties.class)
public class OnboardingConfig {
}
