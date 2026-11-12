package com.byarrival.domain.route.config;

import java.util.Random;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RouteProperties.class, PopularArticleProperties.class})
public class RouteConfig {

    /**
     * 노선 기사 추출과 카드 순서 섞기에 쓴다. 테스트에서 시드를 고정할 수 있게 빈으로 둔다.
     */
    @Bean
    public Random routeRandom() {
        return new Random();
    }
}
