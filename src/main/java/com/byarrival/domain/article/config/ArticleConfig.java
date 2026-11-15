package com.byarrival.domain.article.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    ArticleReadingProperties.class, ArticleSelectionProperties.class, ArticleCrawlProperties.class,
    ArticleSummaryProperties.class, ArticleDifficultyProperties.class, ArticleStoryProperties.class})
public class ArticleConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService articleCrawlExecutor(ArticleCrawlProperties properties) {
        return Executors.newFixedThreadPool(properties.concurrency(), Thread.ofPlatform().name("article-crawl-", 0).factory());
    }

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService articleSummaryExecutor(ArticleSummaryProperties properties) {
        return Executors.newFixedThreadPool(properties.concurrency(), Thread.ofPlatform().name("article-summary-", 0).factory());
    }
}
