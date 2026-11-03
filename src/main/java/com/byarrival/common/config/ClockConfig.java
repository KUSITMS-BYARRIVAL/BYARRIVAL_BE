package com.byarrival.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각 의존 로직이 주입받는 Clock. 서버 기본 시간대와 상관없이 KST를 따른다.
 */
@Configuration
public class ClockConfig {

    public static final String KST = "Asia/Seoul";

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(KST));
    }
}
