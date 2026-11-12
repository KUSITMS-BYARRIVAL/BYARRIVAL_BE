package com.byarrival.domain.quiz.config;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QuizProperties.class)
public class QuizConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService quizGenerationExecutor(QuizProperties properties) {
        return Executors.newFixedThreadPool(properties.concurrency(), Thread.ofPlatform().name("quiz-gen-", 0).factory());
    }

    // 노선 퀴즈 3문제를 고를 때 쓴다
    @Bean
    public Random quizRandom() {
        return new Random();
    }
}
