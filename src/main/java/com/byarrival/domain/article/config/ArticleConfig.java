package com.byarrival.domain.article.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ArticleReadingProperties.class)
public class ArticleConfig {
}
