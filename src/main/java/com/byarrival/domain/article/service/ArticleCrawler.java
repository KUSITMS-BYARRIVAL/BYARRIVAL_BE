package com.byarrival.domain.article.service;

import com.byarrival.domain.article.config.ArticleCrawlProperties;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticlePageClient.Page;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RSS 기사 링크에서 언론사 원문을 찾아 본문을 정제한다. 원문을 가져오지 못했거나, 본문을 찾지 못했거나(최소 길이 미만),
 * 본문이 한국어가 아니면 이유를 로그로 남기고 empty를 돌려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleCrawler {

    private final GoogleNewsUrlResolver googleNewsUrlResolver;
    private final ArticlePageClient pageClient;
    private final ArticleBodyExtractor bodyExtractor;
    private final ArticleContentCleaner contentCleaner;
    private final ArticleTextInspector textInspector;
    private final ArticleCrawlProperties properties;

    public Optional<CrawledArticle> crawl(String link) {
        try {
            Page page = pageClient.get(googleNewsUrlResolver.resolve(link));
            if (page.url().length() > Article.MAX_SOURCE_URL_LENGTH) {
                log.warn("[ArticleCrawl] 원문 주소가 너무 길어 제외 - link={}", link);
                return Optional.empty();
            }
            Optional<String> content = bodyExtractor.extract(page.body(), page.url())
                .flatMap(contentCleaner::clean)
                .filter(text -> text.codePointCount(0, text.length()) >= properties.minContentLength());
            if (content.isEmpty()) {
                log.warn("[ArticleCrawl] 본문을 찾지 못함 - sourceUrl={}", page.url());
                return Optional.empty();
            }
            if (!textInspector.isKorean(content.get())) {
                log.warn("[ArticleCrawl] 한국어 본문이 아니라 제외 - sourceUrl={}", page.url());
                return Optional.empty();
            }
            return Optional.of(new CrawledArticle(page.url(), content.get()));
        } catch (RuntimeException e) {
            log.warn("[ArticleCrawl] 원문을 가져오지 못함 - link={} {}", link, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @param sourceUrl      리다이렉트를 따라간 뒤의 언론사 원문 주소
     * @param cleanedContent 정제한 본문
     */
    public record CrawledArticle(String sourceUrl, String cleanedContent) {
    }
}
