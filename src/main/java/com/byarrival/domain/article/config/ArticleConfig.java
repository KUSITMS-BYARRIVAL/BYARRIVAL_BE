package com.byarrival.domain.article.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    ArticleReadingProperties.class, ArticleSelectionProperties.class, ArticleCrawlProperties.class})
public class ArticleConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService articleCrawlExecutor(ArticleCrawlProperties properties) {
        return Executors.newFixedThreadPool(properties.concurrency(), Thread.ofPlatform().name("article-crawl-", 0).factory());
    }
}
