package com.byarrival.domain.notification.auto;

import java.util.Random;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AutoNotificationProperties.class)
public class AutoNotificationConfig {

    /**
     * 알림 제목 후보를 고른다. 테스트에서 시드를 고정할 수 있게 빈으로 둔다.
     */
    @Bean
    public Random notificationRandom() {
        return new Random();
    }
}
